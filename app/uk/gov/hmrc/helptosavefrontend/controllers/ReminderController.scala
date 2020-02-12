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
import cats.instances.option._
import cats.instances.string._
import cats.syntax.eq._
import cats.syntax.traverse._
import cats.instances.future._
import com.google.inject.{Inject, Singleton}
import play.api.mvc.{Action, Result ⇒ PlayResult, _}
import play.api.{Configuration, Environment, Logger}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.auth.HelpToSaveAuth
import uk.gov.hmrc.helptosavefrontend.config.{ErrorHandler, FrontendAppConfig}
import uk.gov.hmrc.helptosavefrontend.forms.{ReminderForm, ReminderFrequencyValidation}
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.reminder.{DateToDaysMapper, HtsUser}
import uk.gov.hmrc.helptosavefrontend.repo.SessionStore
import uk.gov.hmrc.helptosavefrontend.services.{HelpToSaveReminderService, HelpToSaveService}
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, Email, Logging, NINOLogMessageTransformer, toFuture}
import uk.gov.hmrc.helptosavefrontend.views.html.closeaccount.close_account_are_you_sure
import uk.gov.hmrc.helptosavefrontend.views.html.email.accountholder.check_your_email
import uk.gov.hmrc.helptosavefrontend.views.html.reminder.{reminder_confirmation, reminder_frequency_set}
import uk.gov.hmrc.helptosavefrontend.views.html.register.{bank_account_details, not_eligible}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class ReminderController @Inject() (val helpToSaveReminderService: HelpToSaveReminderService,
                                    val helpToSaveService:         HelpToSaveService,
                                    val sessionStore:              SessionStore,
                                    val authConnector:             AuthConnector,
                                    val metrics:                   Metrics,
                                    val auditor:                   HTSAuditor,
                                    cpd:                           CommonPlayDependencies,
                                    mcc:                           MessagesControllerComponents,
                                    errorHandler:                  ErrorHandler,
                                    reminderFrequencySet:          reminder_frequency_set,
                                    reminderConfirmation:          reminder_confirmation,
                                    notEligible:                   not_eligible,
                                    checkYourEmail:                check_your_email,
                                    closeAccountAreYouSure:        close_account_are_you_sure)(implicit val crypto: Crypto,
                                                                                               implicit val transformer:                 NINOLogMessageTransformer,
                                                                                               implicit val reminderFrequencyValidation: ReminderFrequencyValidation,
                                                                                               val frontendAppConfig:                    FrontendAppConfig,
                                                                                               val config:                               Configuration,
                                                                                               val env:                                  Environment,
                                                                                               ec:                                       ExecutionContext)

  extends BaseController(cpd, mcc, errorHandler) with HelpToSaveAuth with SessionBehaviour with Logging {
  private val eligibilityPage: String = routes.EligibilityCheckController.getIsEligible().url

  private def backLinkFromSession(session: HTSSession): String =
    if (session.changingDetails) {
      routes.EmailController.getSelectEmailPage().url
    } else {
      eligibilityPage
    }

  def getSelectRendersPage(): Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒

    Ok(reminderFrequencySet(ReminderForm.giveRemindersDetailsForm()))

  }(loginContinueURL = routes.ReminderController.selectRemindersSubmit().url)

  def selectRemindersSubmit(): Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒
    ReminderForm.giveRemindersDetailsForm().bindFromRequest().fold(
      withErrors ⇒ {

        Ok(reminderFrequencySet(withErrors))
      },

      success ⇒
        {
          //Get the name
          val userName = htsContext.userDetails match {
            case Left(x)  ⇒ ""
            case Right(x) ⇒ x.forename

          }

          helpToSaveService.getConfirmedEmail.fold({
            err ⇒
              SeeOther(routes.ReminderController.getRendersConfirmPage("noEmail", success.reminderFrequency).url)
          }, {
            emailRetrieved ⇒
              {
                val htsUserToModify = HtsUser(Nino(htsContext.nino), emailRetrieved.getOrElse("noUSeEmail"), userName, daysToReceive = DateToDaysMapper.d2dMapper.getOrElse(success.reminderFrequency, Seq(1)))

                val noParserVal = helpToSaveReminderService.updateHtsUser(htsUserToModify).fold[Future[PlayResult]](
                  {
                    err ⇒
                      {
                        SeeOther(routes.EligibilityCheckController.getCheckEligibility().url)

                      }
                  },
                  succesfulReturn ⇒

                    if (succesfulReturn === "SUCCESS") {

                      Ok(reminderConfirmation(emailRetrieved.getOrElse("noUseEmail"), success.reminderFrequency))

                    } else {
                    //  Ok(reminderConfirmation("failedService", "failedFrequecy"))
                      SeeOther(routes.EligibilityCheckController.getCheckEligibility().url)
                    }


                )

                Ok(reminderConfirmation(emailRetrieved.getOrElse("noUseEmail"), success.reminderFrequency))

              }
          })
        }
    )
  }(loginContinueURL = routes.ReminderController.selectRemindersSubmit().url)

  def getRendersConfirmPage(email: String, period: String): Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒

    crypto.decrypt(email) match {
      case Success(value) ⇒ Ok(reminderConfirmation(value, period))
      case Failure(e)     ⇒ Ok(reminderConfirmation("emailNotDecrypt", period))
    }

  }(loginContinueURL = routes.ReminderController.getRendersConfirmPage(email, period).url)

}
