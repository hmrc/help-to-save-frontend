/*
 * Copyright 2017 HM Revenue & Customs
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

import javax.inject.Singleton

import cats.instances.future._
import com.google.inject.Inject
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.config.{FrontendAppConfig, FrontendAuthConnector}
import uk.gov.hmrc.helptosavefrontend.connectors.NSIConnector.SubmissionFailure
import uk.gov.hmrc.helptosavefrontend.connectors._
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, Email, Logging, NINO, toFuture}
import uk.gov.hmrc.helptosavefrontend.util.Logging._
import uk.gov.hmrc.helptosavefrontend.views
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class RegisterController @Inject() (val messagesApi:           MessagesApi,
                                    val helpToSaveService:     HelpToSaveService,
                                    val sessionCacheConnector: SessionCacheConnector,
                                    frontendAuthConnector:     FrontendAuthConnector,
                                    metrics:                   Metrics,
                                    auditor:                   HTSAuditor
)(implicit ec: ExecutionContext, crypto: Crypto)
  extends HelpToSaveAuth(frontendAuthConnector, metrics) with EnrolmentCheckBehaviour with SessionBehaviour with I18nSupport with Logging {

  import RegisterController.NSIUserInfoOps

  def getConfirmDetailsPage: Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒
    checkIfAlreadyEnrolled { () ⇒
      checkIfDoneEligibilityChecks {
        case (nsiUserInfo, _) ⇒
          checkIfAccountCreateAllowed(
            Ok(views.html.register.confirm_details(nsiUserInfo))
          )
      }
    }
  }(redirectOnLoginURL = FrontendAppConfig.checkEligibilityUrl)

  def confirmEmail(confirmedEmail: String): Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒
    val nino = htsContext.nino
    checkIfAlreadyEnrolled { () ⇒
      checkIfDoneEligibilityChecks {
        case (nsiUserInfo, _) ⇒
          val result = for {
            _ ← sessionCacheConnector.put(HTSSession(Some(nsiUserInfo), Some(confirmedEmail)))
            _ ← helpToSaveService.storeConfirmedEmail(confirmedEmail)
          } yield ()

          result.fold[Result](
            { e ⇒
              logger.warn(s"Could not write confirmed email: $e", nino)
              internalServerError()
            }, { _ ⇒
              SeeOther(routes.RegisterController.getCreateAccountHelpToSavePage().url)
            }
          )
      }
    }
  }(redirectOnLoginURL = FrontendAppConfig.checkEligibilityUrl)

  def getCreateAccountHelpToSavePage: Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒
    checkIfAlreadyEnrolled { () ⇒
      checkIfDoneEligibilityChecks {
        case (_, confirmedEmail) ⇒
          confirmedEmail.fold[Future[Result]](
            SeeOther(routes.RegisterController.getConfirmDetailsPage().url))(
              _ ⇒ Ok(views.html.register.create_account_help_to_save()))
      }
    }
  }(redirectOnLoginURL = FrontendAppConfig.checkEligibilityUrl)

  def getUserCapReachedPage: Action[AnyContent] = authorisedForHts { implicit request ⇒ implicit htsContext ⇒
    Ok(views.html.register.user_cap_reached())
  }(redirectOnLoginURL = FrontendAppConfig.checkEligibilityUrl) //TODO

  def createAccountHelpToSave: Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒
    val nino = htsContext.nino
    checkIfAlreadyEnrolled { () ⇒
      checkIfDoneEligibilityChecks {
        case (nsiUserInfo, confirmedEmail) ⇒
          confirmedEmail.fold[Future[Result]](
            SeeOther(routes.RegisterController.getConfirmDetailsPage().url)
          ) { email ⇒
              // TODO: plug in actual pages below
              val userInfo = nsiUserInfo.updateEmail(email)
              helpToSaveService.createAccount(userInfo).leftMap(submissionFailureToString).fold(
                error ⇒ {
                  logger.warn(s"Error while trying to create account: $error", nino)
                  internalServerError()
                },
                _ ⇒ {
                  // Account creation is successful, trigger background tasks but don't worry about the result
                  auditor.sendEvent(AccountCreated(userInfo), nino)

                  helpToSaveService.updateUserCount().value.onFailure {
                    case e ⇒ logger.warn(s"Could not update the user count, future failed: $e", nino)
                  }

                  helpToSaveService.enrolUser().value.onComplete {
                    case Failure(e)        ⇒ logger.warn(s"Could not start process to enrol user, future failed: $e", nino)
                    case Success(Left(e))  ⇒ logger.warn(s"Could not start process to enrol user: $e", nino)
                    case Success(Right(_)) ⇒ logger.info(s"Process started to enrol user", nino)
                  }

                  SeeOther(routes.NSIController.goToNSI().url)
                }
              )
            }
      }
    }
  }(redirectOnLoginURL = FrontendAppConfig.checkEligibilityUrl)

  private def checkIfAccountCreateAllowed(ifAllowed: ⇒ Result)(implicit hc: HeaderCarrier) = {
    helpToSaveService.isAccountCreationAllowed().fold(
      error ⇒ {
        logger.warn(s"Could not check if account create is allowed, due to: $error")
        ifAllowed
      }, { allowed ⇒
        if (allowed) {
          ifAllowed
        } else {
          SeeOther(routes.RegisterController.getUserCapReachedPage().url)
        }
      }
    )
  }

  /**
   * Checks the HTSSession data from keystore - if the is no session the user has not done the eligibility
   * checks yet this session and they are redirected to the 'apply now' page. If the session data indicates
   * that they are not eligible show the user the 'you are not eligible page'. Otherwise, perform the
   * given action if the the session data indicates that they are eligible
   */
  private def checkIfDoneEligibilityChecks(ifEligible: (NSIUserInfo, Option[Email]) ⇒ Future[Result])(
      implicit
      htsContext: HtsContextWithNINO, hc: HeaderCarrier, request: Request[_]): Future[Result] =
    checkSession {
      // no session data => user has not gone through the journey this session => take them to eligibility checks
      SeeOther(routes.EligibilityCheckController.getCheckEligibility().url)
    } {
      session ⇒
        session.eligibilityCheckResult.fold[Future[Result]](
          // user has gone through journey already this sessions and were found to be ineligible
          SeeOther(routes.EligibilityCheckController.getIsNotEligible().url)
        )(userInfo ⇒
            // user has gone through journey already this sessions and were found to be eligible
            ifEligible(userInfo, session.confirmedEmail)
          )
    }

  private def submissionFailureToString(failure: SubmissionFailure): String =
    s"Account creation failed. ErrorId: ${failure.errorMessageId.getOrElse("-")}, error: ${failure.errorMessage}}"
}

object RegisterController {

  private[controllers] implicit class NSIUserInfoOps(val nsiUserInfo: NSIUserInfo) extends AnyVal {
    def updateEmail(email: String): NSIUserInfo =
      nsiUserInfo.copy(contactDetails = nsiUserInfo.contactDetails.copy(email = email))
  }

}
