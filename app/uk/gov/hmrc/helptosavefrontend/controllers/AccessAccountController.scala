/*
 * Copyright 2022 HM Revenue & Customs
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

import com.google.inject.{Inject, Singleton}
import play.api.mvc._
import play.api.{Configuration, Environment}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosavefrontend.auth.HelpToSaveAuth
import uk.gov.hmrc.helptosavefrontend.config.{ErrorHandler, FrontendAppConfig}
import uk.gov.hmrc.helptosavefrontend.connectors.HelpToSaveReminderConnector
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.models.reminder.CancelHtsUserReminder
import uk.gov.hmrc.helptosavefrontend.models.{HTSSession, HtsContextWithNINO}
import uk.gov.hmrc.helptosavefrontend.repo.SessionStore
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.Logging._
import uk.gov.hmrc.helptosavefrontend.util.{Logging, MaintenanceSchedule, NINOLogMessageTransformer, toFuture}
import uk.gov.hmrc.helptosavefrontend.views.html.core.{confirm_check_eligibility, error_template}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDate
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AccessAccountController @Inject() (
  val helpToSaveService: HelpToSaveService,
  val helpToSaveReminderConnector: HelpToSaveReminderConnector,
  val authConnector: AuthConnector,
  val metrics: Metrics,
  sessionStore: SessionStore,
  cpd: CommonPlayDependencies,
  mcc: MessagesControllerComponents,
  errorHandler: ErrorHandler,
  maintenanceSchedule: MaintenanceSchedule,
  confirmCheckEligibility: confirm_check_eligibility,
  errorTemplate: error_template
)(
  implicit val transformer: NINOLogMessageTransformer,
  val frontendAppConfig: FrontendAppConfig,
  val config: Configuration,
  val env: Environment,
  ec: ExecutionContext
) extends BaseController(cpd, mcc, errorHandler, maintenanceSchedule) with HelpToSaveAuth with EnrolmentCheckBehaviour
    with Logging {

  def getSignInPage: Action[AnyContent] = unprotected { _ ⇒ _ ⇒
    SeeOther("https://www.gov.uk/sign-in-help-to-save")
  }

  def accessAccount: Action[AnyContent] =
    authorisedForHtsWithNINO { implicit request ⇒
      implicit htsContext ⇒
        (() =>
          helpToSaveService
            .getAccount(htsContext.nino, UUID.randomUUID())
            .fold(
              e => {
                logger.warn(s"error retrieving Account details from NS&I, error = $e", htsContext.nino)
                Future.successful({})
              }, { account =>
                if (account.isClosed) {
                  val cancelHtsUserReminder = CancelHtsUserReminder(htsContext.nino)
                  helpToSaveReminderConnector.cancelHtsUserReminders(cancelHtsUserReminder).fold(
                    { _ =>
                      logger.info("Reminders Failed to cancel for Closed Account User on Login")
                    }, { _ =>
                      logger.info("Reminders Canceled for Closed Account User on Login")
                    }
                  )
                } else {
                  val accountEndDate = account.bonusTerms.lastOption.fold[Option[LocalDate]](None)(bonusTerm => Some(bonusTerm.endDate))
                  accountEndDate match {
                    case Some(updatedEndDate) => {
                      val htsUserSchedule = helpToSaveReminderConnector.getHtsUser(htsContext.nino)
                      htsUserSchedule.flatMap { htsUserScheduleToUpdate =>
                        helpToSaveReminderConnector.updateHtsUser(
                          htsUserScheduleToUpdate.copy(endDate = Some(updatedEndDate)))
                      }.fold(
                        { _ =>
                          logger.info(s"endDate field of htsUserSchedule updated to: $updatedEndDate")
                        }, { _ =>
                          logger.info("endDate field of htsUserSchedule not updated")
                        }
                      )
                    }
                    case _ => Future.successful({})
                  }
                }
              }
            )).apply().flatMap(_ => redirectToAccountHolderPage(appConfig.nsiManageAccountUrl))
    }(loginContinueURL = frontendAppConfig.accessAccountUrl)

  def payIn: Action[AnyContent] =
    authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
      redirectToAccountHolderPage(appConfig.nsiPayInUrl)
    }(loginContinueURL = frontendAppConfig.accessAccountUrl)

  def getNoAccountPage: Action[AnyContent] =
    authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
      sessionStore.get.value.flatMap(
        _.fold(
          { e ⇒
            logger.warn(s"Could not get session data: $e")
            internalServerError()
          }, { session ⇒
            checkIfEnrolled(
              { () ⇒
                Ok(confirmCheckEligibility())
              }, { _ ⇒
                SeeOther(routes.EligibilityCheckController.getCheckEligibility.url)
              },
              () ⇒
                SeeOther(
                  session
                    .flatMap(_.attemptedAccountHolderPageURL)
                    .getOrElse(appConfig.nsiManageAccountUrl)
                )
            )
          }
        )
      )

    }(loginContinueURL = routes.AccessAccountController.getNoAccountPage.url)

  private def redirectToAccountHolderPage(pageURL: String)(
    implicit
    htsContext: HtsContextWithNINO,
    request: Request[AnyContent],
    hc: HeaderCarrier
  ): Future[Result] = {

    def storeAttemptedRedirectThenRedirect(redirectTo: String): Future[Result] =
      sessionStore
        .store(HTSSession.empty.copy(attemptedAccountHolderPageURL = Some(pageURL)))
        .value
        .map {
          _.fold[Result]({ e ⇒
            logger.warn(s"Could not store session data: $e")
            internalServerError()
          }, _ ⇒ SeeOther(redirectTo))
        }

    checkIfEnrolled(
      {
        // not enrolled
        () ⇒
          storeAttemptedRedirectThenRedirect(routes.AccessAccountController.getNoAccountPage.url)
      }, {
        // enrolment check error
        e ⇒
          logger.warn(s"Could not check enrolment ($e) - proceeding to check eligibility", htsContext.nino)
          storeAttemptedRedirectThenRedirect(routes.EligibilityCheckController.getCheckEligibility.url)
      }, { () ⇒
        // enrolled
        SeeOther(pageURL)
      }
    )
  }
}
