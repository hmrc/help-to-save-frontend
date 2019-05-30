/*
 * Copyright 2019 HM Revenue & Customs
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

import java.time.temporal.TemporalAdjusters
import java.time.{Clock, LocalDate}

import cats.data.EitherT
import cats.instances.future._
import cats.instances.option._
import cats.instances.string._
import cats.syntax.eq._
import cats.syntax.traverse._
import com.google.inject.Inject
import javax.inject.Singleton
import play.api.i18n.MessagesApi
import play.api.mvc._
import play.api.{Application, Configuration, Environment}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosavefrontend.auth.HelpToSaveAuth
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.controllers.RegisterController.{CreateAccountError, EligibleWithInfo}
import uk.gov.hmrc.helptosavefrontend.forms.EmailValidation
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.models.HTSSession.EligibleWithUserInfo
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityReason
import uk.gov.hmrc.helptosavefrontend.models.register.CreateAccountRequest
import uk.gov.hmrc.helptosavefrontend.models.userinfo.NSIPayload
import uk.gov.hmrc.helptosavefrontend.repo.SessionStore
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveServiceImpl.SubmissionFailure
import uk.gov.hmrc.helptosavefrontend.util.Logging._
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, Email, Logging, NINOLogMessageTransformer, toFuture}
import uk.gov.hmrc.helptosavefrontend.{util, views}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RegisterController @Inject() (val helpToSaveService: HelpToSaveService,
                                    val sessionStore:      SessionStore,
                                    val authConnector:     AuthConnector,
                                    val metrics:           Metrics,
                                    app:                   Application)(implicit val crypto: Crypto,
                                                                        emailValidation:          EmailValidation,
                                                                        override val messagesApi: MessagesApi,
                                                                        val transformer:          NINOLogMessageTransformer,
                                                                        val frontendAppConfig:    FrontendAppConfig,
                                                                        val config:               Configuration,
                                                                        val env:                  Environment,
                                                                        ec:                       ExecutionContext)

  extends BaseController with HelpToSaveAuth with EnrolmentCheckBehaviour with SessionBehaviour with CapCheckBehaviour with Logging {

  val earlyCapCheckOn: Boolean = frontendAppConfig.getBoolean("enable-early-cap-check")
  val clock: Clock = Clock.systemUTC()

  def getCreateAccountPage: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    checkIfAlreadyEnrolled { () ⇒
      checkIfDoneEligibilityChecks { eligibleWithInfo ⇒
        sessionStore.store(eligibleWithInfo.session.copy(changingDetails = false)).fold({ e ⇒
          logger.warn(s"Could not write to session cache: $e")
          internalServerError()
        }, { _ ⇒
          EligibilityReason.fromEligible(eligibleWithInfo.userInfo.eligible).fold {
            logger.warn(s"Could not parse eligibility reason: ${eligibleWithInfo.userInfo.eligible}", eligibleWithInfo.userInfo.userInfo.nino)
            internalServerError()
          } { reason ⇒
            eligibleWithInfo.session.bankDetails match {
              case Some(bankDetails) ⇒
                Ok(views.html.register.create_account(eligibleWithInfo.userInfo, eligibleWithInfo.email, bankDetails))
              case None ⇒ SeeOther(routes.BankAccountController.getBankDetailsPage().url)
            }
          }
        })
      }
    }
  }(loginContinueURL = routes.RegisterController.getCreateAccountPage().url)

  def getDailyCapReachedPage: Action[AnyContent] = authorisedForHts { implicit request ⇒ implicit htsContext ⇒
    Ok(views.html.register.daily_cap_reached())
  }(loginContinueURL = routes.RegisterController.getDailyCapReachedPage().url)

  def getTotalCapReachedPage: Action[AnyContent] = authorisedForHts { implicit request ⇒ implicit htsContext ⇒
    Ok(views.html.register.total_cap_reached())
  }(loginContinueURL = routes.RegisterController.getTotalCapReachedPage().url)

  def getServiceUnavailablePage: Action[AnyContent] = authorisedForHts { implicit request ⇒ implicit htsContext ⇒
    Ok(views.html.register.service_unavailable())
  }(loginContinueURL = routes.RegisterController.getServiceUnavailablePage().url)

  def getDetailsAreIncorrect: Action[AnyContent] = authorisedForHts { implicit request ⇒ implicit htsContext ⇒
    Ok(views.html.register.details_are_incorrect())
  }(loginContinueURL = frontendAppConfig.checkEligibilityUrl)

  def createAccount: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    val nino = htsContext.nino
    checkIfAlreadyEnrolled { () ⇒
      checkIfDoneEligibilityChecks { eligibleWithInfo ⇒
        eligibleWithInfo.session.bankDetails match {
          case Some(bankDetails) ⇒
            val payload =
              NSIPayload(eligibleWithInfo.userInfo.userInfo, eligibleWithInfo.email, frontendAppConfig.version, frontendAppConfig.systemId)
                .copy(nbaDetails = Some(bankDetails))

            val createAccountRequest = CreateAccountRequest(payload, eligibleWithInfo.userInfo.eligible.value.eligibilityCheckResult.reasonCode)

            val result = for {
              submissionSuccess ← helpToSaveService.createAccount(createAccountRequest).leftMap(s ⇒ CreateAccountError(Left(s)))
              _ ← {
                val update = submissionSuccess.accountNumber.map(a ⇒
                  sessionStore.store(eligibleWithInfo.session.copy(accountNumber = a.accountNumber)))
                update.map(res => res.leftMap(s ⇒ CreateAccountError(Right(s))))
                //update.traverse[util.Result, Unit](identity).leftMap(s ⇒ CreateAccountError(Right(s)))
              }
            } yield submissionSuccess.accountNumber

            result.map[Result] {

            }

            result.fold[Result]({
              case CreateAccountError(e) ⇒
                e.fold({ submissionFailure ⇒
                  logger.warn(s"Error while trying to create account: ${submissionFailureToString(submissionFailure)}", nino)
                  submissionFailure.errorMessageId.fold(
                    SeeOther(routes.RegisterController.getCreateAccountErrorPage().url)) { id ⇒
                      if (id === "ZYRC0703" || id === "ZYRC0707") {
                        SeeOther(routes.RegisterController.getCreateAccountErrorBankDetailsPage().url)
                      } else {
                        SeeOther(routes.RegisterController.getCreateAccountErrorPage().url)
                      }
                    }
                }, {
                  error ⇒
                    logger.warn(s"Error while trying to create account: $error", nino)
                    SeeOther(routes.RegisterController.getCreateAccountErrorPage().url)
                })
            }, {
              _.fold(
                SeeOther(frontendAppConfig.nsiManageAccountUrl)
              )(_ ⇒ SeeOther(routes.RegisterController.getAccountCreatedPage().url))
            })

          case None ⇒
            logger.warn("no bank details found in session, redirect user to bank_details page")
            SeeOther(routes.BankAccountController.getBankDetailsPage().url)
        }
      }
    }
  }(loginContinueURL = routes.RegisterController.createAccount().url)

  def getAccountCreatedPage: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    val result = for {
      enrolmentStatus ← helpToSaveService.getUserEnrolmentStatus()
      session ← enrolmentStatus.fold[util.Result[Option[HTSSession]]](EitherT.pure[Future, String](None), { _ ⇒ sessionStore.get })
    } yield session

    result.fold({ e ⇒
      logger.warn(s"Could not get enrolment status or session: $e")
      internalServerError()
    }, { session ⇒
      val accountNumberAndEmail: Option[(String, Email)] = for {
        s ← session
        a ← s.accountNumber
        e ← s.confirmedEmail
      } yield (a, e)

      accountNumberAndEmail.fold(SeeOther(routes.EligibilityCheckController.getCheckEligibility().url)) {
        case (accountNumber, email) ⇒
          val lastDayOfMonth = LocalDate.now(clock).`with`(TemporalAdjusters.lastDayOfMonth())
          Ok(views.html.register.account_created(accountNumber, email, lastDayOfMonth))
      }
    })
  }(loginContinueURL = routes.RegisterController.getCreateAccountPage().url)

  def getCreateAccountErrorPage: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    checkIfAlreadyEnrolled { () ⇒
      checkIfDoneEligibilityChecks {
        _ ⇒ Ok(views.html.register.create_account_error())
      }
    }
  }(loginContinueURL = routes.RegisterController.getCreateAccountPage().url)

  def getCreateAccountErrorBankDetailsPage: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    checkIfAlreadyEnrolled { () ⇒
      checkIfDoneEligibilityChecks {
        _ ⇒ Ok(views.html.register.create_account_error_bank_details())
      }
    }
  }(loginContinueURL = routes.RegisterController.getCreateAccountErrorBankDetailsPage().url)

  def changeEmail: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    checkIfAlreadyEnrolled { () ⇒
      checkIfDoneEligibilityChecks { eligibleWithInfo ⇒
        startChangingDetailsAndRedirect(eligibleWithInfo.session, routes.EmailController.getSelectEmailPage().url)
      }
    }
  }(loginContinueURL = routes.RegisterController.changeEmail().url)

  def changeBankDetails: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    checkIfAlreadyEnrolled { () ⇒
      checkIfDoneEligibilityChecks { eligibleWithInfo ⇒
        startChangingDetailsAndRedirect(eligibleWithInfo.session, routes.BankAccountController.getBankDetailsPage().url)
      }
    }
  }(loginContinueURL = routes.RegisterController.changeBankDetails().url)

  def getCannotCheckDetailsPage: Action[AnyContent] = Action { implicit request ⇒
    implicit val htsContext: HtsContext = HtsContext(authorised = false)
    Ok(views.html.cannot_check_details())
  }

  private def startChangingDetailsAndRedirect(session: HTSSession, redirectTo: String)(implicit request: Request[_], hc: HeaderCarrier): Future[Result] =
    sessionStore.store(session.copy(changingDetails = true)).fold({ e ⇒
      logger.warn(s"Could not write to session cache: $e")
      internalServerError()
    },
      _ ⇒ SeeOther(redirectTo)
    )

  /**
   * Checks the HTSSession data from mongo - if the is no session the user has not done the eligibility
   * checks yet this session and they are redirected to the 'apply now' page. If the session data indicates
   * that they are not eligible show the user the 'you are not eligible page'. Otherwise, perform the
   * given action if the the session data indicates that they are eligible
   */
  private def checkIfDoneEligibilityChecks(
      ifEligibleWithInfo: EligibleWithInfo ⇒ Future[Result])(
      implicit
      htsContext: HtsContextWithNINO, hc: HeaderCarrier, request: Request[_]): Future[Result] =
    checkSession {
      // no session data => user has not gone through the journey this session => take them to eligibility checks
      SeeOther(routes.EligibilityCheckController.getCheckEligibility().url)
    } {
      session ⇒
        session.eligibilityCheckResult.fold[Future[Result]](
          SeeOther(routes.EligibilityCheckController.getCheckEligibility().url)
        )(_.fold(
            // user has gone through journey already this sessions and were found to be ineligible
            _ ⇒ SeeOther(routes.EligibilityCheckController.getIsNotEligible().url),
            userInfo ⇒
              //by this time user should have gone through email journey and have verified/confirmed email stored in the session
              session.confirmedEmail
                .fold(
                  toFuture(SeeOther(routes.EmailController.getSelectEmailPage().url))
                )(email ⇒ ifEligibleWithInfo(EligibleWithInfo(userInfo, email, session))
                  )
          ))
    }

  private def submissionFailureToString(failure: SubmissionFailure): String =
    s"Account creation failed. ErrorId: ${failure.errorMessageId.getOrElse("-")}, errorMessage: ${failure.errorMessage}, errorDetails: ${failure.errorDetail}"
}

object RegisterController {

  case class EligibleWithInfo(userInfo: EligibleWithUserInfo, email: String, session: HTSSession)

  private case class CreateAccountError(error: Either[SubmissionFailure, String])

}
