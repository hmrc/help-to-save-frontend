/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.helptosavefrontend.controllers

import cats.data.EitherT
import cats.instances.future._
import cats.instances.string._
import cats.syntax.eq._
import com.google.inject.Inject
import play.api.Application
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.config.FrontendAuthConnector
import uk.gov.hmrc.helptosavefrontend.connectors.{EmailVerificationConnector, NSIConnector}
import uk.gov.hmrc.helptosavefrontend.controllers.AccountHolderUpdateEmailAddressController.UpdateEmailError
import uk.gov.hmrc.helptosavefrontend.forms.{UpdateEmail, UpdateEmailForm}
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.Logging._
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, Email, EmailVerificationParams, toFuture}
import uk.gov.hmrc.helptosavefrontend.views
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class AccountHolderUpdateEmailAddressController @Inject() (val helpToSaveService:          HelpToSaveService,
                                                           frontendAuthConnector:          FrontendAuthConnector,
                                                           val emailVerificationConnector: EmailVerificationConnector,
                                                           nSIConnector:                   NSIConnector,
                                                           metrics:                        Metrics,
                                                           val auditor:                    HTSAuditor
)(implicit app: Application, crypto: Crypto, val messagesApi: MessagesApi, ec: ExecutionContext)
  extends HelpToSaveAuth(frontendAuthConnector, metrics)
  with VerifyEmailBehaviour with I18nSupport {

  def getUpdateYourEmailAddress(): Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒
    checkIfAlreadyEnrolled(email ⇒
      Ok(views.html.email.update_email_address(email, UpdateEmailForm.verifyEmailForm))
    )
  }(redirectOnLoginURL = routes.AccountHolderUpdateEmailAddressController.getUpdateYourEmailAddress().url)

  def onSubmit(): Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒
    checkIfAlreadyEnrolled(_ ⇒
      UpdateEmailForm.verifyEmailForm.bindFromRequest().fold(
        formWithErrors ⇒ {
          BadRequest(views.html.email.update_email_address("errors", formWithErrors))
        },
        (details: UpdateEmail) ⇒
          sendEmailVerificationRequest(
            details.email,
            Ok(views.html.register.check_your_email(details.email)),
            params ⇒ routes.AccountHolderUpdateEmailAddressController.emailVerified(params.encode()).url,
            isNewApplicant = false)

      ))
  }(redirectOnLoginURL = routes.AccountHolderUpdateEmailAddressController.onSubmit().url)

  def emailVerified(emailVerificationParams: String): Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒
    handleEmailVerified(
      emailVerificationParams,
      params ⇒
        checkIfAlreadyEnrolled (_ ⇒ handleEmailVerified(params))
    )
  }(redirectOnLoginURL = routes.AccountHolderUpdateEmailAddressController.emailVerified(emailVerificationParams).url)

  def getEmailUpdated: Action[AnyContent] = authorisedForHts { implicit request ⇒ implicit htsContext: HtsContext ⇒
    Ok(views.html.email.we_updated_your_email())
  }(redirectOnLoginURL = routes.AccountHolderUpdateEmailAddressController.getEmailUpdated().url)

  def getEmailUpdateError: Action[AnyContent] = authorisedForHts { implicit request ⇒ implicit htsContext: HtsContext ⇒
    Ok(views.html.email.we_couldnt_update_your_email())
  }(redirectOnLoginURL = routes.AccountHolderUpdateEmailAddressController.getEmailUpdateError().url)

  private def handleEmailVerified(emailVerificationParams: EmailVerificationParams)(
      implicit
      request:    Request[AnyContent],
      htsContext: HtsContextWithNINO
  ): Future[Result] = {
    val nino = htsContext.nino

    if (emailVerificationParams.nino =!= nino) {
      auditor.sendEvent(SuspiciousActivity(None, s"nino_mismatch, expected=$nino, received=${emailVerificationParams.nino}"), nino)
      logger.warn(s"SuspiciousActivity: email was verified but nino [${emailVerificationParams.nino}] in URL did not match user's nino", nino)
      internalServerError()
    } else {
      htsContext.userDetails match {

        case Left(missingUserInfos) ⇒
          logger.warn("Email was verified but missing some user info " +
            s"(${missingUserInfos.missingInfo.mkString(",")}", nino)
          internalServerError()

        case Right(nsiUserInfo) ⇒
          val oldEmail = nsiUserInfo.contactDetails.email
          val result: EitherT[Future, UpdateEmailError, Unit] = for {
            _ ← nSIConnector.updateEmail(nsiUserInfo.updateEmail(emailVerificationParams.email)).leftMap(UpdateEmailError.NSIError)
            _ ← helpToSaveService.storeConfirmedEmail(emailVerificationParams.email).leftMap[UpdateEmailError](UpdateEmailError.EmailMongoError)
          } yield ()

          result.fold({
            case UpdateEmailError.NSIError(e) ⇒
              logger.warn(s"Could not update email with NS&I: $e", nino)
              SeeOther(routes.AccountHolderUpdateEmailAddressController.getEmailUpdateError().url)

            case UpdateEmailError.EmailMongoError(e) ⇒
              logger.warn("Email updated with NS&I but could not write email to email mongo store. Redirecting back to NS&I", nino)
              auditor.sendEvent(EmailChanged(nino, oldEmail, emailVerificationParams.email), nino)
              SeeOther(uk.gov.hmrc.helptosavefrontend.controllers.routes.NSIController.goToNSI().url)
          }, { _ ⇒
            logger.info("Successfully updated email with NS&I", nino)
            auditor.sendEvent(EmailChanged(nino, oldEmail, emailVerificationParams.email), nino)
            SeeOther(routes.AccountHolderUpdateEmailAddressController.getEmailUpdated().url)
          })
      }
    }
  }

  /**
   * Use the enrolment store and email store to see if the user is enrolled
   */
  private def checkIfAlreadyEnrolled(ifEnrolled: Email ⇒ Future[Result])(
      implicit
      htsContext: HtsContextWithNINO,
      hc:         HeaderCarrier,
      request:    Request[_]
  ): Future[Result] = {
    val enrolled: EitherT[Future, String, (EnrolmentStatus, Option[Email])] = for {
      enrolmentStatus ← helpToSaveService.getUserEnrolmentStatus()
      maybeEmail ← helpToSaveService.getConfirmedEmail()
    } yield (enrolmentStatus, maybeEmail)

    enrolled.fold[Future[Result]]({
      error ⇒
        logger.warn(s"Could not check enrolment status: $error")
        internalServerError()
    }, {
      case (enrolmentStatus, maybeEmail) ⇒
        val nino = htsContext.nino

        (enrolmentStatus, maybeEmail) match {
          case (EnrolmentStatus.NotEnrolled, _) ⇒
            // user is not enrolled in this case
            logger.warn("SuspiciousActivity: missing HtS enrolment record for user", nino)
            auditor.sendEvent(SuspiciousActivity(Some(nino), "missing_enrolment"), nino)
            internalServerError()

          case (EnrolmentStatus.Enrolled(_), None) ⇒
            // this should never happen since we cannot have created an account
            // without a successful write to our email store
            logger.warn("SuspiciousActivity: user is enrolled but the HtS email record does not exist", nino)
            auditor.sendEvent(SuspiciousActivity(Some(nino), "missing_email_record"), nino)
            internalServerError()

          case (EnrolmentStatus.Enrolled(_), Some(email)) ⇒
            ifEnrolled(email)

        }
    }).flatMap(identity)
  }
}

object AccountHolderUpdateEmailAddressController {

  private sealed trait UpdateEmailError

  private object UpdateEmailError {

    case class NSIError(message: String) extends UpdateEmailError

    case class EmailMongoError(message: String) extends UpdateEmailError

  }

}

