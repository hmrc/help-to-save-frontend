/*
 * Copyright 2021 HM Revenue & Customs
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

import cats.instances.future._
import com.google.inject.{Inject, Singleton}
import play.api.mvc.{Result => PlayResult, _}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosavefrontend.auth.HelpToSaveAuth
import uk.gov.hmrc.helptosavefrontend.config.{ErrorHandler, FrontendAppConfig}
import uk.gov.hmrc.helptosavefrontend.forms.{BankDetails, BankDetailsValidation}
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.models.{HTSSession, ValidateBankDetailsRequest}
import uk.gov.hmrc.helptosavefrontend.repo.SessionStore
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.{toFuture, _}
import uk.gov.hmrc.helptosavefrontend.views.html.register.{bank_account_details, not_eligible}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BankAccountController @Inject() (
  val helpToSaveService: HelpToSaveService,
  val sessionStore: SessionStore,
  val authConnector: AuthConnector,
  val metrics: Metrics,
  cpd: CommonPlayDependencies,
  mcc: MessagesControllerComponents,
  errorHandler: ErrorHandler,
  maintenanceSchedule: MaintenanceSchedule,
  bankAccountDetails: bank_account_details,
  notEligible: not_eligible
)(
  implicit val transformer: NINOLogMessageTransformer,
  val frontendAppConfig: FrontendAppConfig,
  val config: Configuration,
  val env: Environment,
  bankDetailsValidation: BankDetailsValidation,
  ec: ExecutionContext
) extends BaseController(cpd, mcc, errorHandler, maintenanceSchedule) with HelpToSaveAuth with EnrolmentCheckBehaviour
    with SessionBehaviour with EnrollAndEligibilityCheck {
  val isFeatureEnabled: Boolean = frontendAppConfig.reminderServiceFeatureSwitch
  private def backLinkFromSession(session: HTSSession): String =
    if (session.changingDetails) {
      routes.RegisterController.getCreateAccountPage.url
    } else {
      if (session.pendingEmail.isDefined) {
        routes.EmailController.getEmailConfirmed.url
      } else if (!isFeatureEnabled) {
        routes.EmailController.getSelectEmailPage.url
      } else if (session.hasSelectedReminder) {
        routes.ReminderController.getApplySavingsReminderSignUpPage.url
      } else {
        routes.ReminderController.getApplySavingsReminderPage.url
      }
    }

  def getBankDetailsPage(): Action[AnyContent] =
    authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
      checkIfAlreadyEnrolledAndDoneEligibilityChecks { s ⇒
        s.bankDetails.fold(
          Ok(bankAccountDetails(BankDetails.giveBankDetailsForm(), backLinkFromSession(s)))
        )(
          bankDetails ⇒
            Ok(bankAccountDetails(BankDetails.giveBankDetailsForm().fill(bankDetails), backLinkFromSession(s)))
        )
      }
    }(loginContinueURL = routes.BankAccountController.getBankDetailsPage.url)

  def submitBankDetails(): Action[AnyContent] =
    authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
      checkIfAlreadyEnrolledAndDoneEligibilityChecks { session ⇒
        BankDetails
          .giveBankDetailsForm()
          .bindFromRequest()
          .fold(
            withErrors ⇒ Ok(bankAccountDetails(withErrors, backLinkFromSession(session))), { bankDetails ⇒
              helpToSaveService
                .validateBankDetails(
                  ValidateBankDetailsRequest(htsContext.nino, bankDetails.sortCode.toString, bankDetails.accountNumber)
                )
                .fold[Future[PlayResult]](
                  error ⇒ {
                    logger.warn(s"Could not validate bank details due to : $error")
                    internalServerError()
                  }, { result ⇒
                    if (result.isValid && result.sortCodeExists) {
                      sessionStore
                        .store(session.copy(bankDetails = Some(bankDetails)))
                        .fold(
                          error ⇒ {
                            logger.warn(s"Could not update session with bank details: $error")
                            internalServerError()
                          },
                          _ ⇒ SeeOther(routes.RegisterController.getCreateAccountPage.url)
                        )
                    } else {
                      val formWithErrors = if (result.isValid && !result.sortCodeExists) {
                        BankDetails
                          .giveBankDetailsForm()
                          .fill(bankDetails)
                          .withError("sortCode", BankDetailsValidation.ErrorMessages.sortCodeBackendInvalid)
                      } else {
                        BankDetails
                          .giveBankDetailsForm()
                          .fill(bankDetails)
                          .withError("sortCode", BankDetailsValidation.ErrorMessages.sortCodeBackendInvalid)
                          .withError("accountNumber", BankDetailsValidation.ErrorMessages.accountNumberBackendInvalid)
                      }

                      Ok(bankAccountDetails(formWithErrors, backLinkFromSession(session)))
                    }
                  }
                )
                .flatMap(identity _)
            }
          )
      }

    }(loginContinueURL = routes.BankAccountController.submitBankDetails.url)
}
