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
import uk.gov.hmrc.helptosavefrontend.forms.{BankDetailsValidation, EmailValidation, ReminderForm, SelectEmailForm, UpdateEmailForm}
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

  def getselectRendersPage(): Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒

      Ok(reminderFrequencySet(ReminderForm.giveRemindersDetailsForm()))
  }

  def onSubmit(): Action[AnyContent] = Action.async { implicit request ⇒

    ReminderForm.giveRemindersDetailsForm().bindFromRequest().fold(
      withErrors ⇒ Ok(reminderFrequencySet(withErrors)),
      {
        form ⇒  Ok(reminderFrequencySet(ReminderForm.giveRemindersDetailsForm()))
      }

    )


  }

  def selectRemindersSubmit(): Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒

    def handleForm(email:    String,
                   backLink: Option[String],
                   session:  HTSSession
                  ): Future[PlayResult] =
      SelectEmailForm.selectEmailForm.bindFromRequest().fold(
        withErrors ⇒ Ok(selectEmail(email, withErrors, backLink)),
        { form ⇒
          val (updatedSession, result) = form.newEmail.fold {
            session.copy(hasSelectedEmail = true) →
              SeeOther(routes.EmailController.emailConfirmed(crypto.encrypt(email)).url)
          } { newEmail ⇒
            session.copy(pendingEmail     = Some(newEmail), confirmedEmail = None, hasSelectedEmail = true) →
              SeeOther(routes.EmailController.confirmEmail().url)
          }

          if (updatedSession =!= session) {
            updateSessionAndReturnResult(updatedSession, result)
          } else {
            result
          }
        }
      )

    def ifDigitalNewApplicant = { maybeSession: Option[HTSSession] ⇒
      withEligibleSession({
        case (session, eligibleWithEmail) ⇒
          val backLink = backLinkFromSession(session)
          handleForm(eligibleWithEmail.email,
            Some(backLink),
            session
          )
      }, {
        case _ ⇒ SeeOther(routes.EmailController.getGiveEmailPage().url)
      })(maybeSession)
    }

    def ifDE = { maybeSession: Option[HTSSession] ⇒
      maybeSession.fold[Future[Result]](
        SeeOther(routes.EligibilityCheckController.getCheckEligibility().url)
      ) { session ⇒
        session.pendingEmail.fold[Future[Result]] {
          logger.warn("Could not find pending email for select email submit")
          internalServerError()
        } {
          email ⇒ handleForm(email, None, session)
        }
      }
    }

    checkSessionAndEnrolmentStatus(ifDigitalNewApplicant, ifDE)

  }(loginContinueURL = routes.EmailController.selectEmailSubmit().url)





  private def checkIfAlreadyEnrolled(ifEnrolled: Email ⇒ Future[PlayResult], path: String)(
      implicit
      htsContext: HtsContextWithNINO,
      hc:         HeaderCarrier,
      request:    Request[_]
  ): Future[PlayResult] = {
    val enrolled: EitherT[Future, String, (EnrolmentStatus, Option[Email])] = for {
      enrolmentStatus ← helpToSaveService.getUserEnrolmentStatus()
      maybeEmail ← helpToSaveService.getConfirmedEmail()
    } yield (enrolmentStatus, maybeEmail)

    enrolled
      .leftMap {
        error ⇒
          logger.warn(s"Could not check enrolment status: $error")
          SeeOther(routes.EmailController.confirmEmailErrorTryLater().url)
      }
      .semiflatMap {
        case (enrolmentStatus, maybeEmail) ⇒
          val nino = htsContext.nino

          (enrolmentStatus, maybeEmail) match {
            case (EnrolmentStatus.NotEnrolled, _) ⇒
              // user is not enrolled in this case
              logger.warn("SuspiciousActivity: missing HtS enrolment record for user")
              auditor.sendEvent(SuspiciousActivity(Some(nino), "missing_enrolment", path), nino)
              SeeOther(routes.EmailController.confirmEmailErrorTryLater().url)

            case (EnrolmentStatus.Enrolled(_), None) ⇒
              // this should never happen since we cannot have created an account
              // without a successful write to our email store
              logger.warn("SuspiciousActivity: user is enrolled but the HtS email record does not exist")
              auditor.sendEvent(SuspiciousActivity(Some(nino), "missing_email_record", path), nino)
              SeeOther(routes.EmailController.confirmEmailErrorTryLater().url)

            case (EnrolmentStatus.Enrolled(_), Some(email)) ⇒
              ifEnrolled(email)
          }
      }.merge
  }
}
