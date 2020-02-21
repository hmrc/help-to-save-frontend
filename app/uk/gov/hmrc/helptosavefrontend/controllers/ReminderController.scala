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
import cats.instances.future._
import cats.instances.string._
import cats.syntax.eq._
import com.google.inject.{Inject, Singleton}
import play.api.mvc.{Action, _}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.auth.HelpToSaveAuth
import uk.gov.hmrc.helptosavefrontend.config.{ErrorHandler, FrontendAppConfig}
import uk.gov.hmrc.helptosavefrontend.forms.{ReminderForm, ReminderFrequencyValidation}
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.models.reminder.{CancelHtsUserReminder, DateToDaysMapper, DaysToDateMapper, HtsUser}
import uk.gov.hmrc.helptosavefrontend.repo.SessionStore
import uk.gov.hmrc.helptosavefrontend.services.{HelpToSaveReminderService, HelpToSaveService}
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, Logging, NINOLogMessageTransformer, toFuture}
import uk.gov.hmrc.helptosavefrontend.views.html.reminder._

import scala.concurrent.ExecutionContext
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
                                    reminderFrequencyChange:       reminder_frequency_change,
                                    reminderConfirmation:          reminder_confirmation,
                                    reminderCancelConfirmation:    reminder_cancel_confirmation,
                                    reminderDashboard:             reminder_dashboard)(implicit val crypto: Crypto,
                                                                                       implicit val transformer:                 NINOLogMessageTransformer,
                                                                                       implicit val reminderFrequencyValidation: ReminderFrequencyValidation,
                                                                                       val frontendAppConfig:                    FrontendAppConfig,
                                                                                       val config:                               Configuration,
                                                                                       val env:                                  Environment,
                                                                                       ec:                                       ExecutionContext)

  extends BaseController(cpd, mcc, errorHandler) with HelpToSaveAuth with SessionBehaviour with Logging {

  val isFeatureEnabled: Boolean = frontendAppConfig.reminderServiceFeatureSwitch

  private def backLink: String = routes.AccessAccountController.accessAccount().url

  def getSelectRendersPage(): Action[AnyContent] = authorisedForHtsWithNINO{ implicit request ⇒ implicit htsContext ⇒

    if (isFeatureEnabled) {
      // get optin status
      helpToSaveReminderService.getHtsUser(htsContext.nino).fold(
        e ⇒ {
          logger.warn(s"error retrieving Hts User details from reminder${htsContext.nino}")

          Ok(reminderFrequencySet(ReminderForm.giveRemindersDetailsForm(), Some(backLink)))
        },
        {
          htsUser ⇒

            Ok(reminderDashboard(htsUser.email, DaysToDateMapper.reverseMapper.getOrElse(htsUser.daysToReceive, "String"), Some(backLink)))
        }
      )

    } else {
      SeeOther(routes.RegisterController.getServiceUnavailablePage().url)
    }

  }(loginContinueURL = routes.ReminderController.selectRemindersSubmit().url)

  def selectRemindersSubmit(): Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒

    ReminderForm.giveRemindersDetailsForm().bindFromRequest().fold(
      withErrors ⇒ {
        Ok(reminderFrequencySet(withErrors))
      },
      success ⇒
        htsContext.userDetails match {
          case Left(missingUserInfos) ⇒
            logger.warn(s"Email was verified but missing some user info ${missingUserInfos}")
            internalServerError()

          case Right(userInfo) ⇒
            helpToSaveService.getConfirmedEmail.value.flatMap{
              _.fold(
                noEmailError ⇒ {
                  logger.warn(s"An error occurred while accessing confirmed email service for user: ${userInfo.nino} Exception : ${noEmailError}")
                  internalServerError()
                },
                emailRetrieved ⇒
                  emailRetrieved match {
                    case Some(email) if !email.isEmpty ⇒ {

                      val daysToReceiveReminders = DateToDaysMapper.d2dMapper.getOrElse(success.reminderFrequency, Seq())
                      val htsUserToBeUpdated = HtsUser(Nino(htsContext.nino), email, userInfo.forename, true, daysToReceiveReminders)
                      helpToSaveReminderService.updateHtsUser(htsUserToBeUpdated)
                        .fold(
                          htsError ⇒ {
                            logger.warn(s"An error occurred while accessing HTS Reminder service for user: ${userInfo.nino} Error: ${htsError}")
                            internalServerError()
                          },
                          htsUser ⇒ SeeOther(routes.ReminderController.getRendersConfirmPage(crypto.encrypt(htsUser.email), success.reminderFrequency).url)
                        )

                    }
                    case Some(_) ⇒ {
                      logger.warn(s"Empty email retrieved for user: ${userInfo.nino}")
                      internalServerError()
                    }
                  })
            }
        })
  }(loginContinueURL = routes.ReminderController.selectRemindersSubmit().url)

  def getRendersConfirmPage(email: String, period: String): Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒

    crypto.decrypt(email) match {
      case Success(value) ⇒ Ok(reminderConfirmation(value, period))
      case Failure(e) ⇒ {
        logger.warn(s"Could not write confirmed email: $email and the exception : $e")
        internalServerError()
      }
    }

  }(loginContinueURL = routes.ReminderController.getRendersConfirmPage(email, period).url)

  def getSelectedRendersPage(): Action[AnyContent] = authorisedForHtsWithNINO{ implicit request ⇒ implicit htsContext ⇒

    helpToSaveReminderService.getHtsUser(htsContext.nino).fold(
      e ⇒ {
        logger.warn(s"error retrieving Hts User details from reminder${htsContext.nino}")
        internalServerError()
      },
      {
        htsUser ⇒
          Ok(reminderFrequencyChange(ReminderForm.giveRemindersDetailsForm(), Some(backLink)))
      }
    )

  }(loginContinueURL = routes.ReminderController.selectedRemindersSubmit().url)

  def selectedRemindersSubmit(): Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒

    ReminderForm.giveRemindersDetailsForm().bindFromRequest().fold(
      withErrors ⇒ {
        Ok(reminderFrequencySet(withErrors))
      },
      success ⇒
        htsContext.userDetails match {
          case Left(missingUserInfos) ⇒
            logger.warn(s"Email was verified but missing some user info ${missingUserInfos}")
            internalServerError()

          case Right(userInfo) ⇒
            helpToSaveService.getConfirmedEmail.value.flatMap{
              _.fold(
                noEmailError ⇒ {
                  logger.warn(s"An error occurred while accessing confirmed email service for user: ${userInfo.nino} Exception : ${noEmailError}")
                  internalServerError()
                },
                emailRetrieved ⇒
                  emailRetrieved match {
                    case Some(email) if !email.isEmpty ⇒ {
                      if (success.reminderFrequency === "cancel") {
                        val cancelHtsUserReminder = CancelHtsUserReminder(htsContext.nino)
                        helpToSaveReminderService.cancelHtsUserReminders(cancelHtsUserReminder)
                          .fold(
                            htsservError ⇒ {
                              logger.warn(s"An error occurred while accessing HTS Reminder service for user: ${htsContext.nino} Error: ${htsservError}")
                              internalServerError()
                            },
                            _ ⇒ SeeOther(routes.ReminderController.getRendersCancelConfirmPage().url)
                          )

                      } else {
                        val daysToReceiveReminders = DateToDaysMapper.d2dMapper.getOrElse(success.reminderFrequency, Seq())
                        val htsUserToBeUpdated = HtsUser(Nino(htsContext.nino), email, userInfo.forename, true, daysToReceiveReminders)
                        helpToSaveReminderService.updateHtsUser(htsUserToBeUpdated)
                          .fold(
                            htsError ⇒ {
                              logger.warn(s"An error occurred while accessing HTS Reminder service for user: ${userInfo.nino} Error: ${htsError}")
                              internalServerError()
                            },
                            htsUser ⇒ SeeOther(routes.ReminderController.getRendersConfirmPage(crypto.encrypt(htsUser.email), success.reminderFrequency).url)
                          )
                      }
                    }
                    case Some(_) ⇒ {
                      logger.warn(s"Empty email retrieved for user: ${userInfo.nino}")
                      internalServerError()
                    }
                  })
            }
        })
  }(loginContinueURL = routes.ReminderController.selectedRemindersSubmit().url)

  def getRendersCancelConfirmPage(): Action[AnyContent] = authorisedForHtsWithNINO{ implicit request ⇒ implicit htsContext ⇒

    Ok(reminderCancelConfirmation())

  }(loginContinueURL = routes.ReminderController.getRendersCancelConfirmPage().url)

}

