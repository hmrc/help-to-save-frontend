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

import javax.inject.Singleton

import com.google.inject.Inject
import play.api.Application
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import uk.gov.hmrc.helptosavefrontend.config.FrontendAuthConnector
import uk.gov.hmrc.helptosavefrontend.connectors.{EmailVerificationConnector, SessionCacheConnector}
import uk.gov.hmrc.helptosavefrontend.services.EnrolmentService
import uk.gov.hmrc.helptosavefrontend.views
import uk.gov.hmrc.helptosavefrontend.util.toFuture
import uk.gov.hmrc.helptosavefrontend.forms.{UpdateEmail, UpdateEmailForm}
import uk.gov.hmrc.helptosavefrontend.models.VerifyEmailError.{AlreadyVerified, BackendError, RequestNotValidError, VerificationServiceUnavailable}

import scala.concurrent.Future

@Singleton
class UpdateEmailAddressController @Inject()(val sessionCacheConnector: SessionCacheConnector,
                                             val enrolmentService: EnrolmentService,
                                             frontendAuthConnector: FrontendAuthConnector,
                                             emailVerificationConnector: EmailVerificationConnector
                                            )(implicit app: Application, val messagesApi: MessagesApi)
  extends HelpToSaveAuth(app, frontendAuthConnector) with EnrolmentCheckBehaviour with SessionBehaviour with I18nSupport {

  def getUpdateYourEmailAddress: Action[AnyContent] = authorisedForHtsWithInfo {
    implicit request ⇒
      implicit htsContext ⇒
        checkIfAlreadyEnrolled { _ ⇒
          checkSession {
            SeeOther(routes.EligibilityCheckController.getCheckEligibility().url)
          } {
            _.eligibilityCheckResult.fold(
              Ok(views.html.core.not_eligible())
            )( userInfo ⇒ {

              Ok(views.html.register.update_email_address(userInfo.contactDetails.email, Some(UpdateEmailForm.verifyEmailForm)))
            })
          }
        }
  }

  def onSubmit(): Action[AnyContent] = authorisedForHtsWithInfo {
    implicit request =>
      implicit htsContext ⇒
        UpdateEmailForm.verifyEmailForm.bindFromRequest().fold(
          formWithErrors => {
            Future.successful(BadRequest(views.html.register.update_email_address("errors", Some(formWithErrors))))
          },
          details => {
           emailVerificationConnector.verifyEmail(htsContext.nino.getOrElse(""), details.toString).map {
                case Right(x) ⇒ Ok(views.html.register.check_your_email())
                //case Left(RequestNotValidError(nino)) ⇒ Ok(views.html.register.email_verify_error())
               // case Left(AlreadyVerified(nino, newEmail)) ⇒ Ok(views.html.register.email_verify_error())
                //case Left(VerificationServiceUnavailable()) ⇒ Ok(views.html.register.email_verify_error())
               // case Left(BackendError("")) ⇒ Ok(views.html.register.email_verify_error())
             }
          }
        )
  }
}
