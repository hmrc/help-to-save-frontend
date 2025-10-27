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

import cats.data.EitherT
import cats.instances.future._
import cats.instances.option._
import cats.instances.string._
import cats.syntax.eq._
import cats.syntax.traverse._
import com.google.inject.Inject
import play.api.data.Form
import play.api.data.Forms.{mapping, of}
import play.api.mvc._
import play.api.{Configuration, Environment}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.auth.HelpToSaveAuth
import uk.gov.hmrc.helptosavefrontend.config.{ErrorHandler, FrontendAppConfig}
import uk.gov.hmrc.helptosavefrontend.controllers.RegisterController.{CreateAccountError, EligibleWithInfo}
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.models.HTSSession.EligibleWithUserInfo
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityReason
import uk.gov.hmrc.helptosavefrontend.models.register.CreateAccountRequest
import uk.gov.hmrc.helptosavefrontend.models.reminder.{DateToDaysMapper, HtsUserSchedule}
import uk.gov.hmrc.helptosavefrontend.models.userinfo.NSIPayload
import uk.gov.hmrc.helptosavefrontend.repo.SessionStore
import uk.gov.hmrc.helptosavefrontend.models.SubmissionResult.SubmissionFailure
import uk.gov.hmrc.helptosavefrontend.services.{HelpToSaveReminderService, HelpToSaveService}
import uk.gov.hmrc.helptosavefrontend.util
import uk.gov.hmrc.helptosavefrontend.util.Logging._
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, Email, Logging, MaintenanceSchedule, NINOLogMessageTransformer, toFuture}
import uk.gov.hmrc.helptosavefrontend.views.html.cannot_check_details
import uk.gov.hmrc.helptosavefrontend.views.html.register._
import uk.gov.hmrc.http.HeaderCarrier

import java.time.temporal.TemporalAdjusters
import java.time.{Clock, LocalDate, LocalDateTime}
import javax.inject.Singleton
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RegisterController @Inject() (
  val helpToSaveService: HelpToSaveService,
  val helpToSaveReminderService: HelpToSaveReminderService,
  val sessionStore: SessionStore,
  val authConnector: AuthConnector,
  val metrics: Metrics,
  val auditor: HTSAuditor,
  cpd: CommonPlayDependencies,
  mcc: MessagesControllerComponents,
  errorHandler: ErrorHandler,
  maintenanceSchedule: MaintenanceSchedule,
  createAccountView: create_account,
  dailyCapReachedView: daily_cap_reached,
  totalCapReachedView: total_cap_reached,
  serviceUnavailableView: service_unavailable,
  detailsAreIncorrectView: details_are_incorrect,
  accountCreatedView: account_created,
  createAccountErrorView: create_account_error,
  createAccountErrorBankDetailsView: create_account_error_bank_details,
  cannotCheckDetailsView: cannot_check_details
)(
  implicit val crypto: Crypto,
  val transformer: NINOLogMessageTransformer,
  val frontendAppConfig: FrontendAppConfig,
  val config: Configuration,
  val env: Environment,
  ec: ExecutionContext
) extends CustomBaseController(cpd, mcc, errorHandler, maintenanceSchedule) with HelpToSaveAuth
    with EnrolmentCheckBehaviour with SessionBehaviour with CapCheckBehaviour with Logging {

  val clock: Clock = Clock.systemUTC()

  def accessOrPayIn: Action[AnyContent] =
    authorisedForHtsWithNINO { implicit request => implicit htsContext =>
      val result = for {
        enrolmentStatus <- helpToSaveService.getUserEnrolmentStatus()
        session <- enrolmentStatus.fold[util.Result[Option[HTSSession]]](EitherT.pure[Future, String](None), { _ =>
                    sessionStore.get
                  })
      } yield session
      result.fold(
        { e =>
          logger.warn(s"Could not get enrolment status or session: $e")
          internalServerError()
        }, { session =>
          val accountNumberAndEmail: Option[(String, Email)] = for {
            s <- session
            a <- s.accountNumber
            e <- s.confirmedEmail
          } yield (a, e)

          accountNumberAndEmail.fold(SeeOther(routes.EligibilityCheckController.getCheckEligibility.url)) {
            case (accountNumber, email) =>
              val lastDayOfMonth = LocalDate.now(clock).`with`(TemporalAdjusters.lastDayOfMonth())
              this.payNowForm
                .bindFromRequest()
                .fold(
                  e => {
                    Ok(accountCreatedView(e, accountNumber, email, lastDayOfMonth))
                  },
                  payInNow =>
                    if (payInNow) {
                      SeeOther(routes.AccessAccountController.payIn.url)
                    } else {
                      SeeOther(routes.AccessAccountController.accessAccount.url)
                    }
                )
          }
        }
      )
    }(loginContinueURL = routes.RegisterController.getCreateAccountPage.url)

  val payNowForm: Form[Boolean] = {
    Form(
      mapping(
        "payInNow" -> of(BooleanFormatter.formatter)
      )(identity)(Some(_))
    )
  }

  def getCreateAccountPage: Action[AnyContent] =
    authorisedForHtsWithNINO { implicit request => implicit htsContext =>
      checkIfAlreadyEnrolled { () =>
        checkIfDoneEligibilityChecks { eligibleWithInfo =>
          sessionStore
            .store(eligibleWithInfo.session.copy(changingDetails = false))
            .fold(
              { _ =>
                internalServerError()
              }, { _ =>
                EligibilityReason
                  .fromEligible(eligibleWithInfo.userInfo.eligible)
                  .fold {
                    logger.warn(
                      s"Could not parse eligibility reason: ${eligibleWithInfo.userInfo.eligible}",
                      eligibleWithInfo.userInfo.userInfo.nino
                    )
                    internalServerError()
                  } { _ =>
                    val period = eligibleWithInfo.session.reminderDetails.getOrElse("noValue")
                    eligibleWithInfo.session.bankDetails match {
                      case Some(bankDetails) =>
                        Ok(
                          createAccountView(
                            eligibleWithInfo.userInfo,
                            period,
                            eligibleWithInfo.email,
                            bankDetails
                          )
                        )
                      case None => SeeOther(routes.BankAccountController.getBankDetailsPage.url)
                    }
                  }
              }
            )
        }
      }
    }(loginContinueURL = routes.RegisterController.getCreateAccountPage.url)

  def getDailyCapReachedPage: Action[AnyContent] =
    authorisedForHts { implicit request => implicit htsContext =>
      Ok(dailyCapReachedView())
    }(loginContinueURL = routes.RegisterController.getDailyCapReachedPage.url)

  def getTotalCapReachedPage: Action[AnyContent] =
    authorisedForHts { implicit request => implicit htsContext =>
      Ok(totalCapReachedView())
    }(loginContinueURL = routes.RegisterController.getTotalCapReachedPage.url)

  def getServiceUnavailablePage: Action[AnyContent] =
    authorisedForHts { implicit request => implicit htsContext =>
      Ok(serviceUnavailableView("hts.register.service-unavailable.title.h1", None))
    }(loginContinueURL = routes.RegisterController.getServiceUnavailablePage.url)

  def getServiceOutagePage(end: String): Action[AnyContent] = Action { implicit request =>
    implicit val htsContext: HtsContext = HtsContext(authorised = false)
    Ok(serviceUnavailableView("hts.register.service-outage.title.h1", Some(LocalDateTime.parse(end))))
  }
  def getDetailsAreIncorrect: Action[AnyContent] =
    authorisedForHts { implicit request => implicit htsContext =>
      Ok(detailsAreIncorrectView())
    }(loginContinueURL = routes.EligibilityCheckController.getCheckEligibility.url)

  def createAccount: Action[AnyContent] =
    authorisedForHtsWithNINO { implicit request => implicit htsContext =>
      val nino = htsContext.nino
      checkIfAlreadyEnrolled { () =>
        checkIfDoneEligibilityChecks { eligibleWithInfo =>
          eligibleWithInfo.session.bankDetails match {
            case Some(bankDetails) =>
              val payload =
                NSIPayload(
                  eligibleWithInfo.userInfo.userInfo,
                  eligibleWithInfo.email,
                  frontendAppConfig.version,
                  frontendAppConfig.systemId
                ).copy(nbaDetails = Some(bankDetails))

              val createAccountRequest = CreateAccountRequest(
                payload,
                eligibleWithInfo.userInfo.eligible.value.eligibilityCheckResult.reasonCode
              )

              val result = for {
                submissionSuccess <- helpToSaveService
                                      .createAccount(createAccountRequest)
                                      .leftMap(s => CreateAccountError(Left(s)))
                _ <- EitherT.liftF(
                      processReminderServiceRequest(eligibleWithInfo.session.reminderDetails, nino, eligibleWithInfo)
                    )
                _ <- {
                  val update = submissionSuccess.accountNumber.accountNumber
                    .map(a => sessionStore.store(eligibleWithInfo.session.copy(accountNumber = Some(a))))
                  update.traverse[util.Result, Unit](identity).leftMap(s => CreateAccountError(Right(s)))
                }
              } yield submissionSuccess.accountNumber

              result.fold[Result](
                {
                  case CreateAccountError(e) =>
                    e.fold(
                      {
                        submissionFailure =>
                          logger.warn(
                            s"Error while trying to create account: ${submissionFailureToString(submissionFailure)}",
                            nino
                          )
                          submissionFailure.errorMessageId
                            .fold(SeeOther(routes.RegisterController.getCreateAccountErrorPage.url)) { id =>
                              if (id === "ZYRC0703" || id === "ZYRC0707") {
                                SeeOther(routes.RegisterController.getCreateAccountErrorBankDetailsPage.url)
                              } else {
                                SeeOther(routes.RegisterController.getCreateAccountErrorPage.url)
                              }
                            }
                      }, { error =>
                        logger.warn(s"Error while trying to create account: $error", nino)
                        SeeOther(routes.RegisterController.getCreateAccountErrorPage.url)
                      }
                    )
                }, {
                  _.accountNumber.fold(
                    SeeOther(frontendAppConfig.nsiManageAccountUrl)
                  )(_ => SeeOther(routes.RegisterController.getAccountCreatedPage.url))
                }
              )

            case None =>
              logger.warn("no bank details found in session, redirect user to bank_details page")
              SeeOther(routes.BankAccountController.getBankDetailsPage.url)
          }
        }
      }
    }(loginContinueURL = routes.RegisterController.createAccount.url)

  def processReminderServiceRequest(reminderDetails: Option[String], nino: String, eligibleWithInfo: EligibleWithInfo)(
    implicit request: Request[_]
  ): Future[Result] = {
    val daysToReceiveReminders = reminderDetails.getOrElse("None")
    if (daysToReceiveReminders =!= "None") {
      auditor.sendEvent(
        HtsReminderCreatedEvent(
          HtsReminderCreated(
            HTSReminderAccount(
              nino,
              eligibleWithInfo.email,
              eligibleWithInfo.userInfo.userInfo.forename,
              eligibleWithInfo.userInfo.userInfo.surname,
              optInStatus = true,
              DateToDaysMapper.d2dMapper.getOrElse(daysToReceiveReminders, Seq())
            )
          ),
          request.uri
        ),
        nino
      )
      helpToSaveReminderService
        .updateHtsUser(
          HtsUserSchedule(
            Nino(nino),
            eligibleWithInfo.email,
            eligibleWithInfo.userInfo.userInfo.forename,
            eligibleWithInfo.userInfo.userInfo.surname,
            optInStatus = true,
            DateToDaysMapper.d2dMapper.getOrElse(daysToReceiveReminders, Seq())
          )
        )
        .fold(
          { _ =>
            internalServerError()
          }, { htsUser =>
            logger.info(s"reminder updated ${htsUser.nino}")
            SeeOther(routes.RegisterController.getAccountCreatedPage.url)
          }
        )

    } else {
      SeeOther(routes.RegisterController.getAccountCreatedPage.url)
    }
  }

  def getAccountCreatedPage: Action[AnyContent] =
    authorisedForHtsWithNINO { implicit request => implicit htsContext =>
      val result = for {
        enrolmentStatus <- helpToSaveService.getUserEnrolmentStatus()
        session <- enrolmentStatus.fold[util.Result[Option[HTSSession]]](EitherT.pure[Future, String](None), { _ =>
                    sessionStore.get
                  })
      } yield session

      result.fold(
        { e =>
          logger.warn(s"Could not get enrolment status or session: $e")
          internalServerError()
        }, { session =>
          val accountNumberAndEmail: Option[(String, Email)] = for {
            s <- session
            a <- s.accountNumber
            e <- s.confirmedEmail
          } yield (a, e)

          accountNumberAndEmail.fold(SeeOther(routes.EligibilityCheckController.getCheckEligibility.url)) {
            case (accountNumber, email) =>
              val lastDayOfMonth = LocalDate.now(clock).`with`(TemporalAdjusters.lastDayOfMonth())
              Ok(accountCreatedView(payNowForm, accountNumber, email, lastDayOfMonth))
          }
        }
      )
    }(loginContinueURL = routes.RegisterController.getCreateAccountPage.url)

  def getCreateAccountErrorPage: Action[AnyContent] =
    authorisedForHtsWithNINO { implicit request => implicit htsContext =>
      checkIfAlreadyEnrolled { () =>
        checkIfDoneEligibilityChecks { _ =>
          Ok(createAccountErrorView())
        }
      }
    }(loginContinueURL = routes.RegisterController.getCreateAccountPage.url)

  def getCreateAccountErrorBankDetailsPage: Action[AnyContent] =
    authorisedForHtsWithNINO { implicit request => implicit htsContext =>
      checkIfAlreadyEnrolled { () =>
        checkIfDoneEligibilityChecks { _ =>
          Ok(createAccountErrorBankDetailsView())
        }
      }
    }(loginContinueURL = routes.RegisterController.getCreateAccountErrorBankDetailsPage.url)

  def changeEmail: Action[AnyContent] =
    authorisedForHtsWithNINO { implicit request => implicit htsContext =>
      checkIfAlreadyEnrolled { () =>
        checkIfDoneEligibilityChecks { eligibleWithInfo =>
          startChangingDetailsAndRedirect(eligibleWithInfo.session, routes.EmailController.getSelectEmailPage.url)
        }
      }
    }(loginContinueURL = routes.RegisterController.changeEmail.url)

  def changeBankDetails: Action[AnyContent] =
    authorisedForHtsWithNINO { implicit request => implicit htsContext =>
      checkIfAlreadyEnrolled { () =>
        checkIfDoneEligibilityChecks { eligibleWithInfo =>
          startChangingDetailsAndRedirect(
            eligibleWithInfo.session,
            routes.BankAccountController.getBankDetailsPage.url
          )
        }
      }
    }(loginContinueURL = routes.RegisterController.changeBankDetails.url)

  def changeReminder: Action[AnyContent] =
    authorisedForHtsWithNINO { implicit request => implicit htsContext =>
      checkIfAlreadyEnrolled { () =>
        checkIfDoneEligibilityChecks { eligibleWithInfo =>
          startChangingDetailsAndRedirect(
            eligibleWithInfo.session,
            routes.ReminderController.getApplySavingsReminderPage.url
          )
        }
      }
    }(loginContinueURL = routes.RegisterController.changeReminder.url)

  def getCannotCheckDetailsPage: Action[AnyContent] = Action { implicit request =>
    implicit val htsContext: HtsContext = HtsContext(authorised = false)
    Ok(cannotCheckDetailsView())
  }

  private def startChangingDetailsAndRedirect(
    session: HTSSession,
    redirectTo: String
  )(implicit request: Request[_], hc: HeaderCarrier): Future[Result] =
    sessionStore
      .store(session.copy(changingDetails = true))
      .fold({ _ =>
        internalServerError()
      }, _ => SeeOther(redirectTo))

  /**
    * Checks the HTSSession data from mongo - if the is no session the user has not done the eligibility
    * checks yet this session and they are redirected to the 'apply now' page. If the session data indicates
    * that they are not eligible show the user the 'you are not eligible page'. Otherwise, perform the
    * given action if the the session data indicates that they are eligible
    */
  private def checkIfDoneEligibilityChecks(ifEligibleWithInfo: EligibleWithInfo => Future[Result])(
    implicit
    htsContext: HtsContextWithNINO,
    hc: HeaderCarrier,
    request: Request[_]
  ): Future[Result] =
    checkSession {
      // no session data => user has not gone through the journey this session => take them to eligibility checks
      SeeOther(routes.EligibilityCheckController.getCheckEligibility.url)
    } { session =>
      session.eligibilityCheckResult.fold[Future[Result]](
        SeeOther(routes.EligibilityCheckController.getCheckEligibility.url)
      )(
        _.fold(
          // user has gone through journey already this sessions and were found to be ineligible
          _ => SeeOther(routes.EligibilityCheckController.getIsNotEligible.url),
          userInfo =>
            //by this time user should have gone through email journey and have verified/confirmed email stored in the session
            session.confirmedEmail
              .fold(
                toFuture(SeeOther(routes.EmailController.getSelectEmailPage.url))
              )(email => ifEligibleWithInfo(EligibleWithInfo(userInfo, email, session)))
        )
      )
    }

  private def submissionFailureToString(failure: SubmissionFailure): String =
    s"Account creation failed. ErrorId: ${failure.errorMessageId.getOrElse("-")}, errorMessage: ${failure.errorMessage}, errorDetails: ${failure.errorDetail}"
}

object RegisterController {

  case class EligibleWithInfo(userInfo: EligibleWithUserInfo, email: String, session: HTSSession)

  private case class CreateAccountError(error: Either[SubmissionFailure, String])

}
