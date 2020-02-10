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
import play.api.mvc.{Action, Result ⇒ PlayResult, _}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.auth.HelpToSaveAuth
import uk.gov.hmrc.helptosavefrontend.config.{ErrorHandler, FrontendAppConfig}
import uk.gov.hmrc.helptosavefrontend.forms.ReminderForm
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.reminder.{DateToDaysMapper, HtsUser}
import uk.gov.hmrc.helptosavefrontend.repo.SessionStore
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveReminderService
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, Email, Logging, NINOLogMessageTransformer, toFuture}
import uk.gov.hmrc.helptosavefrontend.views.html.closeaccount.close_account_are_you_sure
import uk.gov.hmrc.helptosavefrontend.views.html.email.accountholder.check_your_email
import uk.gov.hmrc.helptosavefrontend.views.html.reminder.{reminder_confirmation, reminder_frequency_set}
import uk.gov.hmrc.helptosavefrontend.views.html.register.{bank_account_details, not_eligible}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReminderController @Inject() (val helpToSaveReminderService: HelpToSaveReminderService,
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
                                                                                               val transformer:       NINOLogMessageTransformer,
                                                                                               val frontendAppConfig: FrontendAppConfig,
                                                                                               val config:            Configuration,
                                                                                               val env:               Environment,
                                                                                               ec:                    ExecutionContext)

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
        Ok(reminderConfirmation("errorOccurred", "25th"))
      },
      {
        success ⇒
          {
            val emailVal = htsContext.userDetails match {
              case Left(x)  ⇒ "noEmail"
              case Right(y) ⇒ y.email.getOrElse("default")
            }
            val htsUserToModify = HtsUser(Nino(htsContext.nino), emailVal, daysToReceive = DateToDaysMapper.d2dMapper.getOrElse(success.first, Seq(1)))
            val result = helpToSaveReminderService.updateHtsUser(htsUserToModify)
            Ok(reminderConfirmation(emailVal, success.first))
          }

      }
    )

  }(loginContinueURL = routes.ReminderController.selectRemindersSubmit().url)

}
