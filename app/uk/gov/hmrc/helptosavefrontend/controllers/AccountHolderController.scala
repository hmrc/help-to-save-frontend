/*
 * Copyright 2018 HM Revenue & Customs
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
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent, Request, Result}
import play.api.{Application, Configuration, Environment}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.auth.HelpToSaveAuth
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.connectors.{EmailVerificationConnector, NSIProxyConnector, SessionCacheConnector}
import uk.gov.hmrc.helptosavefrontend.forms.{EmailValidation, UpdateEmail, UpdateEmailForm}
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.userinfo.NSIUserInfo
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.Logging._
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, Email, EmailVerificationParams, NINOLogMessageTransformer, toFuture}
import uk.gov.hmrc.helptosavefrontend.views
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class AccountHolderController @Inject() (val helpToSaveService:          HelpToSaveService,
                                         val authConnector:              AuthConnector,
                                         val emailVerificationConnector: EmailVerificationConnector,
                                         nSIConnector:                   NSIProxyConnector,
                                         val metrics:                    Metrics,
                                         val auditor:                    HTSAuditor,
                                         sessionCacheConnector:          SessionCacheConnector)(implicit app: Application,
                                                                                                crypto:                   Crypto,
                                                                                                emailValidation:          EmailValidation,
                                                                                                override val messagesApi: MessagesApi,
                                                                                                val transformer:          NINOLogMessageTransformer,
                                                                                                val frontendAppConfig:    FrontendAppConfig,
                                                                                                val config:               Configuration,
                                                                                                val env:                  Environment)
  extends BaseController with HelpToSaveAuth with VerifyEmailBehaviour with EnrolmentCheckBehaviour {

  import AccountHolderController._

  def getUpdateYourEmailAddress(): Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    checkIfAlreadyEnrolled(_ ⇒
      Ok(views.html.email.update_email_address(UpdateEmailForm.verifyEmailForm))
    )
  }(redirectOnLoginURL = routes.AccountHolderController.getUpdateYourEmailAddress().url)

  def onSubmit(): Action[AnyContent] = authorisedForHtsWithNINOAndName { implicit request ⇒ implicit htsContext ⇒
    htsContext.firstName.fold[Future[Result]](
      SeeOther(routes.EmailVerificationErrorController.verifyEmailErrorTryLater().url)
    ) { name ⇒
        checkIfAlreadyEnrolled(_ ⇒
          UpdateEmailForm.verifyEmailForm.bindFromRequest().fold(
            formWithErrors ⇒ {
              BadRequest(views.html.email.update_email_address(formWithErrors))
            },
            (details: UpdateEmail) ⇒
              sessionCacheConnector.put(HTSSession(None, None, Some(details.email)))
                .semiflatMap(_ ⇒
                  sendEmailVerificationRequest(
                    details.email,
                    name,
                    SeeOther(routes.AccountHolderController.getCheckYourEmail().url),
                    params ⇒ routes.AccountHolderController.emailVerifiedCallback(params.encode()).url,
                    _ ⇒ SeeOther(routes.EmailVerificationErrorController.verifyEmailErrorTryLater().url),
                    isNewApplicant = false)
                ).leftMap { e ⇒
                  logger.warn(s"Could not write pending email to session cache: $e")
                  SeeOther(routes.EmailVerificationErrorController.verifyEmailErrorTryLater().url)
                }.merge
          )
        )
      }
  }(redirectOnLoginURL = routes.AccountHolderController.onSubmit().url)

  def getCheckYourEmail: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    val result: EitherT[Future, String, Email] = for {
      maybeSession ← sessionCacheConnector.get
      pendingEmail ← EitherT.fromEither[Future](getEmailFromSession(maybeSession)(_.pendingEmail, "pending email"))
    } yield pendingEmail

    result.fold(
      { e ⇒
        logger.warn(s"Could not get pending email: $e", htsContext.nino)
        SeeOther(routes.EmailVerificationErrorController.verifyEmailErrorTryLater().url)
      }, { pendingEmail ⇒
        Ok(views.html.email.check_your_email(pendingEmail))
      }
    )
  }(redirectOnLoginURL = routes.AccountHolderController.getCheckYourEmail().url)

  def emailVerifiedCallback(emailVerificationParams: String): Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒
    withEmailVerificationParameters(
      emailVerificationParams,
      params ⇒ EitherT.right(checkIfAlreadyEnrolled(oldEmail ⇒ handleEmailVerified(params, oldEmail))),
      EitherT.right(toFuture(SeeOther(routes.EmailVerificationErrorController.verifyEmailErrorTryLater().url)))
    ).leftMap{ e ⇒
        logger.warn(e)
        internalServerError()
      }.merge
  }(redirectOnLoginURL = routes.AccountHolderController.emailVerifiedCallback(emailVerificationParams).url)

  def getEmailVerified: Action[AnyContent] = authorisedForHts { implicit request ⇒ implicit htsContext: HtsContext ⇒
    val result: EitherT[Future, String, String] = for {
      session ← sessionCacheConnector.get
      email ← EitherT.fromEither(getEmailFromSession(session)(_.confirmedEmail, "confirmed email"))
    } yield email

    result.fold(
      { e ⇒
        logger.warn(s"Could not find confirmed email: $e")
        SeeOther(routes.EmailVerificationErrorController.verifyEmailErrorTryLater().url)
      },
      email ⇒ Ok(views.html.email.we_updated_your_email(email))
    )
  }(redirectOnLoginURL = routes.AccountHolderController.getEmailVerified.url)

  def getCloseAccountPage: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    checkIfEnrolledForCloseAccount(
      { // not enrolled
        () ⇒ SeeOther(routes.AccessAccountController.getNoAccountPage().url)
      }, {
        e ⇒
          logger.warn(s"Could not check enrolment during call to close account page ($e)", htsContext.nino)
          internalServerError
      },
      () ⇒ Ok(views.html.close_account_are_you_sure())
    )
  }(redirectOnLoginURL = routes.AccountHolderController.getCloseAccountPage().url)

  private def handleEmailVerified(emailVerificationParams: EmailVerificationParams, oldEmail: String)(
      implicit
      request:    Request[AnyContent],
      htsContext: HtsContextWithNINOAndUserDetails
  ): Future[Result] = {
    val nino = htsContext.nino

    if (emailVerificationParams.nino =!= nino) {
      auditor.sendEvent(SuspiciousActivity(None, s"nino_mismatch, expected=$nino, received=${emailVerificationParams.nino}"), nino)
      logger.warn(s"SuspiciousActivity: email was verified but nino [${emailVerificationParams.nino}] in URL did not match user's nino", nino)
      SeeOther(routes.EmailVerificationErrorController.verifyEmailErrorTryLater().url)
    } else {
      htsContext.userDetails match {

        case Left(missingUserInfos) ⇒
          logger.warn("Email was verified but missing some user info " +
            s"(${missingUserInfos.missingInfo.mkString(",")}", nino)
          SeeOther(routes.EmailVerificationErrorController.verifyEmailErrorTryLater().url)

        case Right(userInfo) ⇒
          val result: EitherT[Future, UpdateEmailError, Unit] = for {
            _ ← nSIConnector.updateEmail(NSIUserInfo(userInfo, emailVerificationParams.email)).leftMap(UpdateEmailError.NSIError)
            _ ← helpToSaveService.storeConfirmedEmail(emailVerificationParams.email).leftMap(UpdateEmailError.EmailMongoError)
            _ ← sessionCacheConnector.put(HTSSession(None, Some(emailVerificationParams.email), None))
              .leftMap[UpdateEmailError](UpdateEmailError.SessionCacheError)
          } yield ()

          result.fold({
            case UpdateEmailError.NSIError(e) ⇒
              logger.warn(s"Could not update email with NS&I: $e", nino)
              SeeOther(routes.EmailVerificationErrorController.verifyEmailErrorTryLater().url)

            case UpdateEmailError.SessionCacheError(e) ⇒
              logger.warn(s"Could not write to session cache: $e", nino)
              auditor.sendEvent(EmailChanged(nino, oldEmail, emailVerificationParams.email), nino)
              // TODO: what is the best course of action here? The email has actually been updated but
              // TODO: we can't display it to them on the next page
              SeeOther(routes.EmailVerificationErrorController.verifyEmailErrorTryLater().url)

            case UpdateEmailError.EmailMongoError(e) ⇒
              logger.warn("Email updated with NS&I but could not write email to email mongo store. Redirecting back to NS&I", nino)
              auditor.sendEvent(EmailChanged(nino, oldEmail, emailVerificationParams.email), nino)
              SeeOther(frontendAppConfig.nsiManageAccountUrl)
          }, { _ ⇒
            logger.info("Successfully updated email with NS&I", nino)
            auditor.sendEvent(EmailChanged(nino, oldEmail, emailVerificationParams.email), nino)
            SeeOther(routes.AccountHolderController.getEmailVerified.url)
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

    enrolled
      .leftMap {
        error ⇒
          logger.warn(s"Could not check enrolment status: $error")
          SeeOther(routes.EmailVerificationErrorController.verifyEmailErrorTryLater().url)
      }
      .semiflatMap {
        case (enrolmentStatus, maybeEmail) ⇒
          val nino = htsContext.nino

          (enrolmentStatus, maybeEmail) match {
            case (EnrolmentStatus.NotEnrolled, _) ⇒
              // user is not enrolled in this case
              logger.warn("SuspiciousActivity: missing HtS enrolment record for user", nino)
              auditor.sendEvent(SuspiciousActivity(Some(nino), "missing_enrolment"), nino)
              SeeOther(routes.EmailVerificationErrorController.verifyEmailErrorTryLater().url)

            case (EnrolmentStatus.Enrolled(_), None) ⇒
              // this should never happen since we cannot have created an account
              // without a successful write to our email store
              logger.warn("SuspiciousActivity: user is enrolled but the HtS email record does not exist", nino)
              auditor.sendEvent(SuspiciousActivity(Some(nino), "missing_email_record"), nino)
              SeeOther(routes.EmailVerificationErrorController.verifyEmailErrorTryLater().url)

            case (EnrolmentStatus.Enrolled(_), Some(email)) ⇒
              ifEnrolled(email)
          }
      }.merge
  }

}

object AccountHolderController {

  private sealed trait UpdateEmailError

  private object UpdateEmailError {

    case class NSIError(message: String) extends UpdateEmailError

    case class EmailMongoError(message: String) extends UpdateEmailError

    case class SessionCacheError(message: String) extends UpdateEmailError

  }

}

