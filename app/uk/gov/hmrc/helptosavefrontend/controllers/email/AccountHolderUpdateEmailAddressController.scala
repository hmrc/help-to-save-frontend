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

package uk.gov.hmrc.helptosavefrontend.controllers.email

import cats.data.EitherT
import cats.instances.future._
import com.google.inject.Inject
import play.api.Application
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Result}
import uk.gov.hmrc.helptosavefrontend.config.{FrontendAppConfig, FrontendAuthConnector}
import uk.gov.hmrc.helptosavefrontend.connectors.EmailVerificationConnector
import uk.gov.hmrc.helptosavefrontend.controllers.HelpToSaveAuth
import uk.gov.hmrc.helptosavefrontend.forms.UpdateEmailForm
import uk.gov.hmrc.helptosavefrontend.models.{EnrolmentStatus, HtsContext}
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, Email, NINO, toFuture}
import uk.gov.hmrc.helptosavefrontend.views
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class AccountHolderUpdateEmailAddressController @Inject() (val helpToSaveService:          HelpToSaveService,
                                                           frontendAuthConnector:          FrontendAuthConnector,
                                                           val emailVerificationConnector: EmailVerificationConnector
)(implicit app: Application, crypto: Crypto, val messagesApi: MessagesApi, ec: ExecutionContext)
  extends HelpToSaveAuth(app, frontendAuthConnector) with VerifyEmailBehaviour with I18nSupport {

  implicit val userType: UserType = UserType.AccountHolder

  def getUpdateYourEmailAddress(): Action[AnyContent] = authorisedForHtsWithInfo{ implicit request ⇒ implicit htsContext ⇒
    checkIfAlreadyEnrolled{
      case (_, email) ⇒
        Ok(views.html.email.update_email_address(email, UpdateEmailForm.verifyEmailForm))
    }
  }(redirectOnLoginURL = FrontendAppConfig.checkEligibilityUrl)

  def onSubmit(): Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒
    checkIfAlreadyEnrolled{
      case (nino, _) ⇒
        sendEmailVerificationRequest(nino)
    }
  } (redirectOnLoginURL = FrontendAppConfig.checkEligibilityUrl)

  def emailVerified(emailVerificationParams: String): Action[AnyContent] = authorisedForHtsWithInfo{ implicit request ⇒ implicit htsContext ⇒
    handleEmailVerified(
      emailVerificationParams,
      // TODO: this is where the call to update NS&I with the new email will happen
      _ ⇒ Ok
    )
  } (redirectOnLoginURL = FrontendAppConfig.checkEligibilityUrl)

  /**
   * Use the enrolment store and email store to see if the user is enrolled
   */
  private def checkIfAlreadyEnrolled(ifEnrolled: (NINO, Email) ⇒ Future[Result])(implicit htsContext: HtsContext, hc: HeaderCarrier): Future[Result] = {
    val enrolled: EitherT[Future, String, (NINO, EnrolmentStatus, Option[Email])] = for {
      nino ← EitherT.fromOption[Future](htsContext.nino, "Could not find NINO")
      enrolmentStatus ← helpToSaveService.getUserEnrolmentStatus(nino)
      maybeEmail ← helpToSaveService.getConfirmedEmail(nino)
    } yield (nino, enrolmentStatus, maybeEmail)

    enrolled.fold[Future[Result]]({
      error ⇒
        logger.warn(s"Could not check enrolment status: $error")
        InternalServerError
    }, {
      case (nino, enrolmentStatus, maybeEmail) ⇒
        (enrolmentStatus, maybeEmail) match {
          case (EnrolmentStatus.NotEnrolled, _) ⇒
            // user is not enrolled in this case
            logger.warn(s"For NINO [$nino]: user was not enrolled")
            InternalServerError

          case (EnrolmentStatus.Enrolled(_), None) ⇒
            // this should never happen since we cannot have created an account
            // without a successful write to our email store
            logger.warn(s"For NINO [$nino]: user was enrolled but had no stored email")
            InternalServerError

          case (EnrolmentStatus.Enrolled(_), Some(email)) ⇒
            ifEnrolled(nino, email)

        }
    }).flatMap(identity)
  }
}

