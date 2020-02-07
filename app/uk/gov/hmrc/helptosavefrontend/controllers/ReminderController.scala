/*
 * Copyright 2020 HM Revenue & Customs
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
import com.google.inject.{Inject, Singleton}
import play.api.mvc.{Action, Result => PlayResult, _}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.auth.HelpToSaveAuth
import uk.gov.hmrc.helptosavefrontend.config.{ErrorHandler, FrontendAppConfig}
import uk.gov.hmrc.helptosavefrontend.connectors.EmailVerificationConnector
import uk.gov.hmrc.helptosavefrontend.forms.{BankDetails, BankDetailsValidation, EmailValidation, ReminderForm, SelectEmailForm, UpdateEmailForm}
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.eligibility.IneligibilityReason
import uk.gov.hmrc.helptosavefrontend.repo.SessionStore
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, Email, NINOLogMessageTransformer, toFuture}
import uk.gov.hmrc.helptosavefrontend.views.html.closeaccount.close_account_are_you_sure
import uk.gov.hmrc.helptosavefrontend.views.html.email.accountholder.check_your_email
import uk.gov.hmrc.helptosavefrontend.views.html.reminder.reminder_frequency_set
import uk.gov.hmrc.helptosavefrontend.views.html.email.{update_email_address, we_updated_your_email}
import uk.gov.hmrc.helptosavefrontend.views.html.register.{bank_account_details, not_eligible}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReminderController @Inject() (val helpToSaveService:          HelpToSaveService,
                                    val sessionStore:               SessionStore,
                                    val emailVerificationConnector: EmailVerificationConnector,
                                    val authConnector:              AuthConnector,
                                    val metrics:                    Metrics,
                                    val auditor:                    HTSAuditor,
                                    cpd:                            CommonPlayDependencies,
                                    mcc:                            MessagesControllerComponents,
                                    errorHandler:                   ErrorHandler,
                                    updateEmailAddress:             update_email_address,
                                    reminderFrequencySet:           reminder_frequency_set,
                                    bankAccountDetails:             bank_account_details,
                                    notEligible:                    not_eligible,
                                    checkYourEmail:                 check_your_email,
                                    weUpdatedYourEmail:             we_updated_your_email,
                                    closeAccountAreYouSure:         close_account_are_you_sure)(implicit val crypto: Crypto,
                                                                                                emailValidation:       EmailValidation,
                                                                                                val transformer:       NINOLogMessageTransformer,
                                                                                                val frontendAppConfig: FrontendAppConfig,
                                                                                                val config:            Configuration,
                                                                                                val env:               Environment,
                                                                                                bankDetailsValidation: BankDetailsValidation,
                                                                                                ec:                    ExecutionContext)

  extends BaseController(cpd, mcc, errorHandler) with HelpToSaveAuth with EnrolmentCheckBehaviour with VerifyEmailBehaviour with SessionBehaviour {
  private val eligibilityPage: String = routes.EligibilityCheckController.getIsEligible().url

  private def backLinkFromSession(session: HTSSession): String =
    if (session.changingDetails) {
      routes.EmailController.getSelectEmailPage().url
    } else {
      eligibilityPage
    }

  /*def getselectRendersPage(): Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒

    checkIfAlreadyEnrolledAndDoneEligibilityChecks(htsContext.nino) {
      s ⇒
        s.bankDetails.fold(
          Ok(bankAccountDetails(BankDetails.giveBankDetailsForm(), backLinkFromSession(s)))
        )(bankDetails ⇒
            Ok(bankAccountDetails(BankDetails.giveBankDetailsForm().fill(bankDetails), backLinkFromSession(s)))
          )
    }
  }(loginContinueURL = routes.BankAccountController.getBankDetailsPage().url)
*/

  def getselectRendersPage(): Action[AnyContent] = authorisedForHtsWithNINO{ implicit request ⇒ implicit htsContext ⇒
    checkIfAlreadyEnrolledAndDoneEligibilityChecks(htsContext.nino) {
      s ⇒
        s.bankDetails.fold(
          Ok(reminderFrequencySet(ReminderForm.giveRemindersDetailsForm(), backLinkFromSession(s)))
        )(bankDetails ⇒
          Ok(reminderFrequencySet(ReminderForm.giveRemindersDetailsForm().fill(bankDetails), backLinkFromSession(s)))
        )
    }
  }(loginContinueURL = routes.ReminderController.getselectRendersPage().url)

  def onSubmit(): Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒
    implicit htsContext ⇒
      checkIfAlreadyEnrolledAndDoneEligibilityChecks(htsContext.nino) {
        s ⇒ReminderForm.giveRemindersDetailsForm().fold(
          withErrors ⇒
            Ok(reminderFrequencySet(withErrors, backLinkFromSession(s))),
          {
            form ⇒ Ok(reminderFrequencySet(ReminderForm.giveRemindersDetailsForm()))
          }
        )
  }

  }

  private def checkIfAlreadyEnrolledAndDoneEligibilityChecks(nino: String)(ifNotEnrolled: HTSSession ⇒ Future[PlayResult])(implicit htsContext: HtsContextWithNINO, request: Request[_]) =
    checkIfAlreadyEnrolled { () ⇒
      checkSession(
        SeeOther(routes.EligibilityCheckController.getCheckEligibility().url)
      ) { session ⇒
        session.eligibilityCheckResult.fold[Future[PlayResult]](
          SeeOther(routes.EligibilityCheckController.getCheckEligibility().url)
        )(_.fold[Future[PlayResult]](
          { ineligibleReason ⇒
            val ineligibilityType = IneligibilityReason.fromIneligible(ineligibleReason)
            val threshold = ineligibleReason.value.threshold

            ineligibilityType.fold {
              logger.warn(s"Could not parse ineligibility reason when storing bank details: $ineligibleReason", nino)
              toFuture(internalServerError())
            } { i ⇒
              toFuture(Ok(notEligible(i, threshold)))
            }
          },
          _ ⇒ ifNotEnrolled(session)
        )
        )
      }
    }


}
