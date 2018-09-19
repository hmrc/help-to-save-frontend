/*
 * Copyright 2018 HM Revenue & Customs
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
import cats.syntax.traverse._
import com.google.inject.Inject
import javax.inject.Singleton
import play.api.i18n.MessagesApi
import play.api.mvc._
import play.api.{Application, Configuration, Environment}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosavefrontend.auth.HelpToSaveAuth
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.connectors._
import uk.gov.hmrc.helptosavefrontend.controllers.RegisterController.EligibleWithInfo
import uk.gov.hmrc.helptosavefrontend.forms.EmailValidation
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.models.HTSSession.EligibleWithUserInfo
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityReason
import uk.gov.hmrc.helptosavefrontend.models.register.CreateAccountRequest
import uk.gov.hmrc.helptosavefrontend.models.userinfo.NSIPayload
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveServiceImpl.SubmissionFailure
import uk.gov.hmrc.helptosavefrontend.util.Logging._
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, NINOLogMessageTransformer, toFuture}
import uk.gov.hmrc.helptosavefrontend.{util, views}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.bootstrap.controller.ActionWithMdc

import scala.concurrent.Future

@Singleton
class RegisterController @Inject() (val helpToSaveService:     HelpToSaveService,
                                    val sessionCacheConnector: SessionCacheConnector,
                                    val authConnector:         AuthConnector,
                                    val metrics:               Metrics,
                                    app:                       Application)(implicit val crypto: Crypto,
                                                                            emailValidation:          EmailValidation,
                                                                            override val messagesApi: MessagesApi,
                                                                            val transformer:          NINOLogMessageTransformer,
                                                                            val frontendAppConfig:    FrontendAppConfig,
                                                                            val config:               Configuration,
                                                                            val env:                  Environment)

  extends BaseController with HelpToSaveAuth with EnrolmentCheckBehaviour with SessionBehaviour with CapCheckBehaviour {

  val earlyCapCheckOn: Boolean = frontendAppConfig.getBoolean("enable-early-cap-check")

  def getCreateAccountPage: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    checkIfAlreadyEnrolled { () ⇒
      checkIfDoneEligibilityChecks { eligibleWithInfo ⇒
        EligibilityReason.fromEligible(eligibleWithInfo.userInfo.eligible).fold {
          logger.warn(s"Could not parse eligiblity reason: ${eligibleWithInfo.userInfo.eligible}", eligibleWithInfo.userInfo.userInfo.nino)
          internalServerError()
        } { _ ⇒
          eligibleWithInfo.session.bankDetails match {
            case Some(bankDetails) ⇒
              Ok(views.html.register.create_account(eligibleWithInfo.userInfo, eligibleWithInfo.email, bankDetails))
            case None ⇒ SeeOther(routes.BankAccountController.getBankDetailsPage().url)
          }

        }
      }
    }
  }(redirectOnLoginURL = routes.RegisterController.getCreateAccountPage().url)

  def getDailyCapReachedPage: Action[AnyContent] = authorisedForHts { implicit request ⇒ implicit htsContext ⇒
    Ok(views.html.register.daily_cap_reached())
  }(redirectOnLoginURL = routes.RegisterController.getDailyCapReachedPage().url)

  def getTotalCapReachedPage: Action[AnyContent] = authorisedForHts { implicit request ⇒ implicit htsContext ⇒
    Ok(views.html.register.total_cap_reached())
  }(redirectOnLoginURL = routes.RegisterController.getTotalCapReachedPage().url)

  def getServiceUnavailablePage: Action[AnyContent] = authorisedForHts { implicit request ⇒ implicit htsContext ⇒
    Ok(views.html.register.service_unavailable())
  }(redirectOnLoginURL = routes.RegisterController.getServiceUnavailablePage().url)

  def getDetailsAreIncorrect: Action[AnyContent] = authorisedForHts { implicit request ⇒ implicit htsContext ⇒
    Ok(views.html.register.details_are_incorrect())
  }(redirectOnLoginURL = frontendAppConfig.checkEligibilityUrl)

  def createAccount: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    val nino = htsContext.nino
    checkIfAlreadyEnrolled { () ⇒
      checkIfDoneEligibilityChecks { eligibleWithInfo ⇒
        eligibleWithInfo.session.bankDetails match {
          case Some(bankDetails) ⇒
            val payload =
              NSIPayload(eligibleWithInfo.userInfo.userInfo, eligibleWithInfo.email, frontendAppConfig.version, frontendAppConfig.systemId)
                .copy(nbaDetails = Some(bankDetails))

            val createAccountRequest = CreateAccountRequest(payload, eligibleWithInfo.userInfo.eligible.value.reasonCode)

            val result = for {
              submissionSuccess ← helpToSaveService.createAccount(createAccountRequest).leftMap(submissionFailureToString)
              _ ← {
                val update = submissionSuccess.accountNumber.map(a ⇒
                  sessionCacheConnector.put(eligibleWithInfo.session.copy(accountNumber = Some(a.accountNumber))))
                update.traverse[util.Result, CacheMap](identity)
              }
            } yield submissionSuccess.accountNumber

            result.fold[Result]({
              e ⇒
                logger.warn(s"Error while trying to create account: $e", nino)
                SeeOther(routes.RegisterController.getCreateAccountErrorPage().url)
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
  }(redirectOnLoginURL = routes.RegisterController.createAccount().url)

  def getAccountCreatedPage: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    val result = for {
      enrolmentStatus ← helpToSaveService.getUserEnrolmentStatus()
      session ← enrolmentStatus.fold[util.Result[Option[HTSSession]]](EitherT.pure[Future, String](None), { _ ⇒ sessionCacheConnector.get })
    } yield session

    result.fold({ e ⇒
      logger.warn(s"Could not get enrolment status or session: $e")
      internalServerError()
    }, {
      _.flatMap(_.accountNumber).fold(SeeOther(routes.EligibilityCheckController.getCheckEligibility().url)){
        accountNumber ⇒
          Ok(views.html.register.account_created(accountNumber))
      }
    })
  }(redirectOnLoginURL = routes.RegisterController.getCreateAccountPage().url)

  def getCreateAccountErrorPage: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    checkIfAlreadyEnrolled { () ⇒
      checkIfDoneEligibilityChecks {
        _ ⇒ Ok(views.html.register.create_account_error())
      }
    }
  }(redirectOnLoginURL = routes.RegisterController.getCreateAccountPage().url)

  def changeEmail: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    checkIfAlreadyEnrolled { () ⇒
      checkIfDoneEligibilityChecks { eligibleWithInfo ⇒
        startChangingDetailsAndRedirect(eligibleWithInfo.session, routes.EmailController.getSelectEmailPage().url)

      }
    }
  }(redirectOnLoginURL = routes.RegisterController.changeEmail().url)

  def changeBankDetails: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    checkIfAlreadyEnrolled { () ⇒
      checkIfDoneEligibilityChecks { eligibleWithInfo ⇒
        startChangingDetailsAndRedirect(eligibleWithInfo.session, routes.BankAccountController.getBankDetailsPage().url)
      }
    }
  }(redirectOnLoginURL = routes.RegisterController.changeBankDetails().url)

  def getCannotCheckDetailsPage: Action[AnyContent] = ActionWithMdc { implicit request ⇒
    implicit val htsContext: HtsContext = HtsContext(authorised = false)
    Ok(views.html.cannot_check_details())
  }

  private def startChangingDetailsAndRedirect(session: HTSSession, redirectTo: String)(implicit request: Request[_], hc: HeaderCarrier): Future[Result] =
    sessionCacheConnector.put(session.copy(changingDetails = true)).fold({ e ⇒
      logger.warn(s"Could not write to session cache: $e")
      internalServerError()
    },
      _ ⇒ SeeOther(redirectTo)
    )

  /**
   * Checks the HTSSession data from keystore - if the is no session the user has not done the eligibility
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
}
