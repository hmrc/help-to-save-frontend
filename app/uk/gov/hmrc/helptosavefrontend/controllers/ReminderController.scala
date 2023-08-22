/*
 * Copyright 2023 HM Revenue & Customs
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
import play.api.mvc._
import play.api.{Configuration, Environment}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.auth.HelpToSaveAuth
import uk.gov.hmrc.helptosavefrontend.config.{ErrorHandler, FrontendAppConfig}
import uk.gov.hmrc.helptosavefrontend.forms.{ReminderForm, ReminderFrequencyValidation}
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.views.html.closeaccount.account_closed
import uk.gov.hmrc.helptosavefrontend.models.{HTSReminderAccount, HTSSession, HtsReminderCancelled, HtsReminderCancelledEvent, HtsReminderCreated, HtsReminderCreatedEvent, HtsReminderUpdated, HtsReminderUpdatedEvent}
import uk.gov.hmrc.helptosavefrontend.models.reminder.{CancelHtsUserReminder, DateToDaysMapper, DaysToDateMapper, HtsUserSchedule}
import uk.gov.hmrc.helptosavefrontend.repo.SessionStore
import uk.gov.hmrc.helptosavefrontend.services.{HelpToSaveReminderService, HelpToSaveService}
import uk.gov.hmrc.helptosavefrontend.util._
import uk.gov.hmrc.helptosavefrontend.views.html.register.not_eligible
import uk.gov.hmrc.helptosavefrontend.views.html.reminder._
import java.util.UUID
import uk.gov.hmrc.helptosavefrontend.controllers.BaseController

import org.joda.time.LocalDate

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class ReminderController @Inject() (
  val helpToSaveReminderService: HelpToSaveReminderService,
  val helpToSaveService: HelpToSaveService,
  val sessionStore: SessionStore,
  val authConnector: AuthConnector,
  val metrics: Metrics,
  val auditor: HTSAuditor,
  cpd: CommonPlayDependencies,
  mcc: MessagesControllerComponents,
  errorHandler: ErrorHandler,
  maintenanceSchedule: MaintenanceSchedule,
  emailSavingsReminder: email_savings_reminders,
  reminderFrequencySet: reminder_frequency_set,
  reminderConfirmation: reminder_confirmation,
  reminderCancelConfirmation: reminder_cancel_confirmation,
  reminderDashboard: reminder_dashboard,
  applySavingsReminders: apply_savings_reminders,
  accountClosed: account_closed,
  notEligible: not_eligible
)(
  implicit val crypto: Crypto,
  implicit val transformer: NINOLogMessageTransformer,
  implicit val reminderFrequencyValidation: ReminderFrequencyValidation,
  val frontendAppConfig: FrontendAppConfig,
  val config: Configuration,
  val env: Environment,
  ec: ExecutionContext
) extends BaseController(cpd, mcc, errorHandler, maintenanceSchedule) with HelpToSaveAuth with SessionBehaviour
    with Logging with EnrolmentCheckBehaviour with EnrollAndEligibilityCheck {

  val isFeatureEnabled: Boolean = frontendAppConfig.reminderServiceFeatureSwitch

  private def backLinkFromSession(session: HTSSession): String =
    if (session.changingDetails) {
      routes.RegisterController.getCreateAccountPage.url
    } else {
      routes.EmailController.getSelectEmailPage.url
    }

  private def backLink: String = routes.AccessAccountController.accessAccount.url

  def getEmailsavingsReminders(): Action[AnyContent] =
    authorisedForHtsWithNINO { implicit request => implicit htsContext =>
      if (isFeatureEnabled) {
        // get optin status
        helpToSaveReminderService
          .getHtsUser(htsContext.nino)
          .fold(
            e => {
              logger.warn(s"error retrieving Hts User details from reminder${htsContext.nino}")
              Ok(emailSavingsReminder(Some(backLink)))
            }, { htsUserSchedule =>
              Ok(
                reminderDashboard(
                  htsUserSchedule.email,
                  DaysToDateMapper.reverseMapper.getOrElse(htsUserSchedule.daysToReceive, "String"),
                  Some(backLink)
                )
              )
            }
          )

      } else {
        SeeOther(routes.RegisterController.getServiceUnavailablePage.url)
      }

    }(loginContinueURL = routes.ReminderController.selectRemindersSubmit.url)

  def getSelectRendersPage(): Action[AnyContent] = {

    authorisedForHtsWithNINO { implicit request =>
      implicit htsContext =>
        helpToSaveService
          .getAccount(htsContext.nino, UUID.randomUUID())
          .fold(
            e => {
              logger.warn(s"error retrieving Account details from NS&I, error = $e")
              def bckLink: String = routes.ReminderController.getEmailsavingsReminders.url
              Ok(
                reminderFrequencySet(
                  ReminderForm.giveRemindersDetailsForm(),
                  "none",
                  "account",
                  Some(bckLink)
                )
              )
            }, { account => {
              if (account.isClosed) {
                def bckLink: String = routes.ReminderController.getEmailsavingsReminders.url
                Ok(accountClosed(Some(bckLink),account.closureDate.getOrElse(LocalDate.now())))
              }
              else if (account.isClosed === false && isFeatureEnabled) {
                def bckLink: String = routes.ReminderController.getEmailsavingsReminders.url
                Ok(
                  reminderFrequencySet(
                    ReminderForm.giveRemindersDetailsForm(),
                    "none",
                    "account",
                    Some(bckLink)
                  )
                )
              } else {
                SeeOther(routes.RegisterController.getServiceUnavailablePage.url)
              }
            }
            })
    }(loginContinueURL = routes.ReminderController.selectRemindersSubmit.url)
  }

  def selectRemindersSubmit(): Action[AnyContent] =
    authorisedForHtsWithInfo { implicit request => implicit htsContext =>
      ReminderForm
        .giveRemindersDetailsForm()
        .bindFromRequest()
        .fold(
          withErrors => {
            Ok(reminderFrequencySet(withErrors, "none", "account"))
          },
          success =>
            htsContext.userDetails match {
              case Left(missingUserInfos) =>
                logger.warn(s"Email was verified but missing some user info $missingUserInfos")
                internalServerError()

              case Right(userInfo) =>
                helpToSaveService.getConfirmedEmail.value.flatMap {
                  _.fold(
                    noEmailError => {
                      logger.warn(
                        s"An error occurred while accessing confirmed email service for user: ${userInfo.nino} Exception : $noEmailError"
                      )
                      internalServerError()
                    },
                    emailRetrieved =>
                      emailRetrieved match {
                        case Some(email) if !email.isEmpty => {
                          val daysToReceiveReminders =
                            DateToDaysMapper.d2dMapper.getOrElse(success.reminderFrequency, Seq())
                          val htsUserToBeUpdated = HtsUserSchedule(
                            Nino(htsContext.nino),
                            email,
                            userInfo.forename,
                            userInfo.surname,
                            true,
                            daysToReceiveReminders
                          )
                          auditor.sendEvent(
                            HtsReminderCreatedEvent(HtsReminderCreated(HTSReminderAccount(htsUserToBeUpdated.nino.value, htsUserToBeUpdated.email, htsUserToBeUpdated.firstName, htsUserToBeUpdated.lastName,htsUserToBeUpdated.optInStatus, htsUserToBeUpdated.daysToReceive)), request.uri),
                            userInfo.nino
                          )
                          helpToSaveReminderService
                            .updateHtsUser(htsUserToBeUpdated)
                            .fold(
                              htsError => {
                                logger.warn(
                                  s"An error occurred while accessing HTS Reminder service for user: ${userInfo.nino} Error: $htsError"
                                )
                                internalServerError()
                              },
                              htsUser =>
                                SeeOther(
                                  routes.ReminderController
                                    .getRendersConfirmPage(
                                      crypto.encrypt(htsUser.email),
                                      success.reminderFrequency,
                                      "Set"
                                    )
                                    .url
                                )
                            )

                        }
                        case Some(_) => {
                          logger.warn(s"Empty email retrieved for user: ${userInfo.nino}")
                          internalServerError()
                        }
                      }
                  )
                }
            }
        )
    }(loginContinueURL = routes.ReminderController.selectRemindersSubmit.url)

  def getRendersConfirmPage(email: String, period: String, page: String): Action[AnyContent] =
    authorisedForHtsWithNINO { implicit request => implicit htsContext =>
      if (isFeatureEnabled) {
        crypto.decrypt(email) match {
          case Success(value) =>
            if (page === "Set") {
              Ok(
                reminderConfirmation(
                  value,
                  period,
                  "hts.reminder-confirmation-set.title.h1",
                  "hts.reminder-confirmation-set.title.p1"
                )
              )
            } else {
              Ok(
                reminderConfirmation(
                  value,
                  period,
                  "hts.reminder-confirmation-update.title.h1",
                  "hts.reminder-confirmation-update.title.p1"
                )
              )
            }

          case Failure(e) => {
            internalServerError()
          }
        }
      } else {
        SeeOther(routes.RegisterController.getServiceUnavailablePage.url)
      }

    }(loginContinueURL = routes.ReminderController.getRendersConfirmPage(email, period, "page").url)

  def getSelectedRendersPage(): Action[AnyContent] = {
    authorisedForHtsWithNINO { implicit request =>
      implicit htsContext =>
        helpToSaveService
          .getAccount(htsContext.nino, UUID.randomUUID())
          .fold(
            e => {
              logger.warn(s"error retrieving Account details from NS&I, error = $e")
              internalServerError()
            }, { account => {
              if (account.isClosed) {
                def bckLink: String = routes.ReminderController.getEmailsavingsReminders.url
                Ok(accountClosed(Some(bckLink),account.closureDate.getOrElse(LocalDate.now())))
              } else {
                SeeOther(routes.ReminderController.accountOpenGetSelectedRendersPage.url)
              }
            }
            })
    }(loginContinueURL = routes.ReminderController.selectRemindersSubmit.url)
  }

  def accountOpenGetSelectedRendersPage(): Action[AnyContent] =
    authorisedForHtsWithNINO { implicit request => implicit htsContext =>
      if (isFeatureEnabled) {
        def bckLink: String = routes.ReminderController.getEmailsavingsReminders.url
        helpToSaveReminderService
          .getHtsUser(htsContext.nino)
          .fold(
            e => {
              logger.warn(s"error retrieving Hts User details from reminder${htsContext.nino}")
              internalServerError()
            }, { htsUser =>
              Ok(
                reminderFrequencySet(
                  ReminderForm.giveRemindersDetailsForm(),
                  DaysToDateMapper.reverseMapper.getOrElse(htsUser.daysToReceive, "String"),
                  "cancel",
                  Some(bckLink)
                )
              )

            }
          )
      } else {
        SeeOther(routes.RegisterController.getServiceUnavailablePage.url)
      }
    }(loginContinueURL = routes.ReminderController.selectedRemindersSubmit.url)

  def selectedRemindersSubmit(): Action[AnyContent] =
    authorisedForHtsWithInfo { implicit request => implicit htsContext =>
      ReminderForm
        .giveRemindersDetailsForm()
        .bindFromRequest()
        .fold(
          withErrors => {
            Ok(reminderFrequencySet(withErrors, "none", "cancel"))
          },
          success =>
            htsContext.userDetails match {
              case Left(missingUserInfos) =>
                logger.warn(s"Email was verified but missing some user info $missingUserInfos")
                internalServerError()

              case Right(userInfo) =>
                helpToSaveService.getConfirmedEmail.value.flatMap {
                  _.fold(
                    noEmailError => {
                      logger.warn(
                        s"An error occurred while accessing confirmed email service for user: ${userInfo.nino} Exception : $noEmailError"
                      )
                      internalServerError()
                    },
                    emailRetrieved =>
                      emailRetrieved match {
                        case Some(email) if !email.isEmpty => {
                          if (success.reminderFrequency === "cancel") {
                            auditor.sendEvent(
                              HtsReminderCancelledEvent(HtsReminderCancelled(
                                userInfo.nino, email), request.uri),
                              userInfo.nino
                            )
                            val cancelHtsUserReminder = CancelHtsUserReminder(htsContext.nino)
                            helpToSaveReminderService
                              .cancelHtsUserReminders(cancelHtsUserReminder)
                              .fold(
                                htsservError => {
                                  logger.warn(
                                    s"An error occurred while accessing HTS Reminder service for user: ${htsContext.nino} Error: $htsservError"
                                  )
                                  internalServerError()
                                },
                                _ => SeeOther(routes.ReminderController.getRendersCancelConfirmPage.url)
                              )

                          } else {
                            val daysToReceiveReminders =
                              DateToDaysMapper.d2dMapper.getOrElse(success.reminderFrequency, Seq())
                            val htsUserToBeUpdated = HtsUserSchedule(
                              Nino(htsContext.nino),
                              email,
                              userInfo.forename,
                              userInfo.surname,
                              true,
                              daysToReceiveReminders
                            )
                            auditor.sendEvent(
                              HtsReminderUpdatedEvent(HtsReminderUpdated(HTSReminderAccount(htsUserToBeUpdated.nino.value, htsUserToBeUpdated.email, htsUserToBeUpdated.firstName, htsUserToBeUpdated.lastName,htsUserToBeUpdated.optInStatus, htsUserToBeUpdated.daysToReceive)), request.uri),
                              userInfo.nino
                            )
                            helpToSaveReminderService
                              .updateHtsUser(htsUserToBeUpdated)
                              .fold(
                                htsError => {
                                  logger.warn(
                                    s"An error occurred while accessing HTS Reminder service for user: ${userInfo.nino} Error: $htsError"
                                  )
                                  internalServerError()
                                },
                                htsUser =>
                                  SeeOther(
                                    routes.ReminderController
                                      .getRendersConfirmPage(
                                        crypto.encrypt(htsUser.email),
                                        success.reminderFrequency,
                                        "Update"
                                      )
                                      .url
                                  )
                              )
                          }
                        }
                        case Some(_) => {
                          internalServerError()
                        }
                      }
                  )
                }
            }
        )
    }(loginContinueURL = routes.ReminderController.selectedRemindersSubmit.url)

  def getRendersCancelConfirmPage(): Action[AnyContent] =
    authorisedForHtsWithNINO { implicit request => implicit htsContext =>
      if (isFeatureEnabled) {
        Ok(reminderCancelConfirmation())
      } else {
        SeeOther(routes.RegisterController.getServiceUnavailablePage.url)
      }
    }(loginContinueURL = routes.ReminderController.getRendersCancelConfirmPage.url)

  def getApplySavingsReminderPage(): Action[AnyContent] =
    authorisedForHtsWithNINO { implicit request => implicit htsContext =>
      if (isFeatureEnabled) {
        checkIfAlreadyEnrolledAndDoneEligibilityChecks { s =>
          Ok(
            applySavingsReminders(
              ReminderForm.giveRemindersDetailsForm(),
              s.reminderValue.getOrElse("none"),
              Some(backLinkFromSession(s))
            )
          )
        }
      } else {
        SeeOther(routes.RegisterController.getServiceUnavailablePage.url)
      }
    }(loginContinueURL = routes.ReminderController.selectRemindersSubmit.url)

  def submitApplySavingsReminderPage(): Action[AnyContent] =
    authorisedForHtsWithNINO { implicit request => implicit htsContext =>
      checkIfAlreadyEnrolledAndDoneEligibilityChecks { s =>
        ReminderForm
          .giveRemindersDetailsForm()
          .bindFromRequest()
          .fold(
            withErrors => {
              Ok(applySavingsReminders(withErrors, "none", Option(routes.EmailController.getSelectEmailPage.url)))
            },
            success =>
              if (success.reminderFrequency === "no") {
                sessionStore
                  .store(
                    s.copy(
                      reminderDetails = Some("none"),
                      reminderValue = Some("no"),
                      hasSelectedReminder = false
                    )
                  )
                  .fold(
                    error => {
                      internalServerError()
                    },
                    if (s.changingDetails) { _ =>
                      SeeOther(routes.RegisterController.getCreateAccountPage.url)
                    } else { _ =>
                      SeeOther(routes.BankAccountController.getBankDetailsPage.url)
                    }
                  )
              } else {
                sessionStore
                  .store(s.copy(reminderValue = Some(success.reminderFrequency), hasSelectedReminder = true))
                  .fold(
                    error => {
                      internalServerError()
                    },
                    _ => SeeOther(routes.ReminderController.getApplySavingsReminderSignUpPage.url)
                  )

              }
          )
      }

    }(loginContinueURL = routes.ReminderController.submitApplySavingsReminderPage.url)

  def getApplySavingsReminderSignUpPage(): Action[AnyContent] =
    authorisedForHtsWithNINO { implicit request => implicit htsContext =>
      if (isFeatureEnabled) {
        checkIfAlreadyEnrolledAndDoneEligibilityChecks { s =>
          def bckLink: String = routes.ReminderController.getApplySavingsReminderPage.url
          s.reminderDetails.fold(
            Ok(
              reminderFrequencySet(
                ReminderForm.giveRemindersDetailsForm(),
                "none",
                "registration",
                Some(bckLink)
              )
            )
          )(
            reminderDetails => {
              Ok(
                reminderFrequencySet(
                  ReminderForm.giveRemindersDetailsForm(),
                  reminderDetails,
                  "registration",
                  Some(bckLink)
                )
              )
            }
          )
        }
      } else {
        SeeOther(routes.RegisterController.getServiceUnavailablePage.url)
      }
    }(loginContinueURL = routes.ReminderController.getApplySavingsReminderSignUpPage.url)

  def submitApplySavingsReminderSignUpPage(): Action[AnyContent] =
    authorisedForHtsWithInfo { implicit request => implicit htsContext =>
      checkIfAlreadyEnrolledAndDoneEligibilityChecks { session =>
        ReminderForm
          .giveRemindersDetailsForm()
          .bindFromRequest()
          .fold(
            withErrors => {
              Ok(
                reminderFrequencySet(
                  withErrors,
                  "none",
                  "registration",
                  Option(routes.EmailController.getSelectEmailPage.url)
                )
              )
            },
            success =>
              if (success.reminderFrequency.nonEmpty) {
                sessionStore
                  .store(if (success.reminderFrequency === "cancel") {
                    session.copy(reminderDetails = Some("none"))
                  } else {
                    session.copy(reminderDetails = Some(success.reminderFrequency))
                  })
                  .fold(
                    error => {
                      internalServerError()
                    },
                    if (session.changingDetails) { _ =>
                      SeeOther(routes.RegisterController.getCreateAccountPage.url)
                    } else { _ =>
                      SeeOther(routes.BankAccountController.getBankDetailsPage.url)
                    }
                  )
              } else {
                internalServerError()
              }
          )
      }

    }(loginContinueURL = routes.ReminderController.submitApplySavingsReminderSignUpPage.url)

}
