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

import cats.data.EitherT
import cats.instances.future._
import cats.syntax.either._
import com.google.inject.Inject
import play.api.Application
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.config.{FrontendAppConfig, FrontendAuthConnector}
import uk.gov.hmrc.helptosavefrontend.connectors.NSIConnector.SubmissionFailure
import uk.gov.hmrc.helptosavefrontend.connectors._
import uk.gov.hmrc.helptosavefrontend.controllers.RegisterController.EligibleInfo.{EligibleWithEmail, EligibleWithNoEmail}
import uk.gov.hmrc.helptosavefrontend.forms.{EmailValidation, GiveEmailForm, SelectEmailForm}
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.userinfo.{NSIUserInfo, UserInfo}
import uk.gov.hmrc.helptosavefrontend.services.{HelpToSaveService, JSONSchemaValidationService}
import uk.gov.hmrc.helptosavefrontend.util.Logging._
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, Email, Logging, PagerDutyAlerting, toFuture}
import uk.gov.hmrc.helptosavefrontend.views
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class RegisterController @Inject() (val messagesApi:             MessagesApi,
                                    val helpToSaveService:       HelpToSaveService,
                                    val sessionCacheConnector:   SessionCacheConnector,
                                    frontendAuthConnector:       FrontendAuthConnector,
                                    jsonSchemaValidationService: JSONSchemaValidationService,
                                    metrics:                     Metrics,
                                    auditor:                     HTSAuditor,
                                    app:                         Application,
                                    pagerDutyAlerting:           PagerDutyAlerting
)(implicit crypto: Crypto, emailValidation: EmailValidation)
  extends HelpToSaveAuth(frontendAuthConnector, metrics)
  with EnrolmentCheckBehaviour with SessionBehaviour with I18nSupport with Logging {

  import uk.gov.hmrc.helptosavefrontend.controllers.RegisterController.CreateAccountError
  import uk.gov.hmrc.helptosavefrontend.controllers.RegisterController.CreateAccountError._

  def getGiveEmailPage: Action[AnyContent] =
    authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
      checkIfAlreadyEnrolled { () ⇒
        checkIfDoneEligibilityChecks{ _ ⇒
          SeeOther(routes.RegisterController.getSelectEmailPage().url)
        }{ _ ⇒
          checkIfAccountCreateAllowed(
            Ok(views.html.register.give_email(GiveEmailForm.giveEmailForm))
          )
        }
      }
    }(redirectOnLoginURL = routes.RegisterController.getGiveEmailPage().url)

  def giveEmailSubmit(): Action[AnyContent] =
    authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
      checkIfAlreadyEnrolled { () ⇒
        checkIfDoneEligibilityChecks { _ ⇒
          SeeOther(routes.RegisterController.getSelectEmailPage().url)
        }{ _ ⇒
          GiveEmailForm.giveEmailForm.bindFromRequest().fold[Result](
            withErrors ⇒ Ok(views.html.register.give_email(withErrors)),
            form ⇒ SeeOther(routes.NewApplicantUpdateEmailAddressController.verifyEmail(form.email).url)
          )
        }
      }
    }(redirectOnLoginURL = routes.RegisterController.giveEmailSubmit().url)

  def getSelectEmailPage: Action[AnyContent] =
    authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
      checkIfAlreadyEnrolled { () ⇒
        checkIfDoneEligibilityChecks { eligibleWithEmail ⇒
          checkIfAccountCreateAllowed(
            Ok(views.html.register.select_email(eligibleWithEmail.email, SelectEmailForm.selectEmailForm)))
        } { _ ⇒
          SeeOther(routes.RegisterController.getGiveEmailPage().url)
        }
      }
    }(redirectOnLoginURL = routes.RegisterController.getSelectEmailPage().url)

  def selectEmailSubmit(): Action[AnyContent] =
    authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
      checkIfAlreadyEnrolled { () ⇒
        checkIfDoneEligibilityChecks{ eligibleWithEmail ⇒
          SelectEmailForm.selectEmailForm.bindFromRequest().fold[Result](
            withErrors ⇒ Ok(views.html.register.select_email(eligibleWithEmail.email, withErrors)),
            _.newEmail.fold(
              SeeOther(routes.RegisterController.confirmEmail(eligibleWithEmail.email).url))(
                newEmail ⇒
                  SeeOther(routes.NewApplicantUpdateEmailAddressController.verifyEmail(newEmail).url)
              )
          )
        } { _ ⇒
          SeeOther(routes.RegisterController.getGiveEmailPage().url)
        }
      }
    }(redirectOnLoginURL = routes.RegisterController.selectEmailSubmit().url)

  def confirmEmail(email: String): Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    val nino = htsContext.nino
    checkIfAlreadyEnrolled { () ⇒
      checkIfDoneEligibilityChecks { eligibleWithEmail ⇒
        val result = for {
          _ ← sessionCacheConnector.put(HTSSession(Right(eligibleWithEmail.userInfo), Some(email)))
          _ ← helpToSaveService.storeConfirmedEmail(email)
        } yield ()

        result.fold[Result](
          { e ⇒
            logger.warn(s"Could not write confirmed email: $e", nino)
            internalServerError()
          }, { _ ⇒
            SeeOther(routes.RegisterController.getCreateAccountHelpToSavePage().url)
          }
        )
      }{ _ ⇒
        SeeOther(routes.RegisterController.getGiveEmailPage().url)
      }
    }
  }(redirectOnLoginURL = FrontendAppConfig.checkEligibilityUrl)

  def getCreateAccountHelpToSavePage: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    checkIfAlreadyEnrolled { () ⇒
      checkIfDoneEligibilityChecks { eligibleWithEmail ⇒
        eligibleWithEmail.confirmedEmail.fold[Future[Result]](
          SeeOther(routes.RegisterController.getSelectEmailPage().url))(
            _ ⇒ Ok(views.html.register.create_account_help_to_save()))
      }{ _ ⇒
        SeeOther(routes.RegisterController.getGiveEmailPage().url)
      }
    }
  }(redirectOnLoginURL = routes.RegisterController.getCreateAccountHelpToSavePage().url)

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
  }(redirectOnLoginURL = FrontendAppConfig.checkEligibilityUrl)

  def createAccountHelpToSave: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    val nino = htsContext.nino
    checkIfAlreadyEnrolled { () ⇒
      checkIfDoneEligibilityChecks { eligibleWithEmail ⇒
        eligibleWithEmail.confirmedEmail.fold[Future[Result]](
          SeeOther(routes.RegisterController.getSelectEmailPage().url)
        ) { confirmedEmail ⇒
            val userInfo = NSIUserInfo(eligibleWithEmail.userInfo, confirmedEmail)

            val result: EitherT[Future, CreateAccountError, Unit] = for {
              _ ← EitherT.fromEither[Future](validateCreateAccountJsonSchema(userInfo).leftMap(JSONSchemaValidationError))
              _ ← helpToSaveService.createAccount(userInfo).leftMap[CreateAccountError](f ⇒ BackendError(submissionFailureToString(f)))
            } yield ()

            result.fold({
              case JSONSchemaValidationError(e) ⇒
                logger.warn(s"user info failed validation for creating account: $e", nino)
                pagerDutyAlerting.alert("JSON schema validation failed")
                internalServerError()

              case BackendError(e) ⇒
                logger.warn(s"Error while trying to create account: $e", nino)
                internalServerError()
            }, { _ ⇒
              logger.info("Successfully created account", nino)

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

              SeeOther(FrontendAppConfig.nsiManageAccountUrl)
            })
          }
      }{ _ ⇒
        SeeOther(routes.RegisterController.getGiveEmailPage().url)
      }
    }
  }(redirectOnLoginURL = routes.RegisterController.createAccountHelpToSave().url)

  private def checkIfAccountCreateAllowed(ifAllowed: ⇒ Result)(implicit hc: HeaderCarrier) = {
    helpToSaveService.isAccountCreationAllowed().fold(
      error ⇒ {
        logger.warn(s"Could not check if account create is allowed, due to: $error")
        ifAllowed
      }, { userCapResponse ⇒
        if (userCapResponse.isTotalCapDisabled && userCapResponse.isDailyCapDisabled) {
          SeeOther(routes.RegisterController.getServiceUnavailablePage().url)
        } else if (userCapResponse.isTotalCapDisabled || userCapResponse.isTotalCapReached) {
          SeeOther(routes.RegisterController.getTotalCapReachedPage().url)
        } else if (userCapResponse.isDailyCapDisabled || userCapResponse.isDailyCapReached) {
          SeeOther(routes.RegisterController.getDailyCapReachedPage().url)
        } else {
          ifAllowed
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
  private def checkIfDoneEligibilityChecks(
      ifEligibleWithEmail: EligibleWithEmail ⇒ Future[Result])(
      ifEligibleWithoutEmail: EligibleWithNoEmail ⇒ Future[Result])(
      implicit
      htsContext: HtsContextWithNINO, hc: HeaderCarrier, request: Request[_]): Future[Result] =
    checkSession {
      // no session data => user has not gone through the journey this session => take them to eligibility checks
      SeeOther(routes.EligibilityCheckController.getCheckEligibility().url)
    } {
      session ⇒
        session.eligibilityCheckResult.fold[Future[Result]](
          // user has gone through journey already this sessions and were found to be ineligible
          _ ⇒ SeeOther(routes.EligibilityCheckController.getIsNotEligible().url),
          userInfo ⇒
            // user has gone through journey already this sessions and were found to be eligible
            userInfo.email.fold(ifEligibleWithoutEmail(EligibleWithNoEmail(userInfo)))(email ⇒
              ifEligibleWithEmail(EligibleWithEmail(userInfo, email, session.confirmedEmail))
            )
        )
    }

  private def validateCreateAccountJsonSchema(userInfo: NSIUserInfo): Either[String, Unit] = {
    import uk.gov.hmrc.helptosavefrontend.util.Toggles._

    FEATURE("create-account-json-validation", app.configuration, logger, Some(userInfo.nino)).thenOrElse(
      jsonSchemaValidationService.validate(Json.toJson(userInfo)).map(_ ⇒ {
      }),
      Right(())
    )
  }

  private def submissionFailureToString(failure: SubmissionFailure): String =
    s"Account creation failed. ErrorId: ${failure.errorMessageId.getOrElse("-")}, errorMessage: ${failure.errorMessage}, errorDetails: ${failure.errorDetail}"

}

object RegisterController {

  sealed trait EligibleInfo

  object EligibleInfo {

    case class EligibleWithEmail(userInfo: UserInfo, email: Email, confirmedEmail: Option[Email]) extends EligibleInfo

    case class EligibleWithNoEmail(userInfo: UserInfo) extends EligibleInfo
  }

  private sealed trait CreateAccountError

  private object CreateAccountError {

    case class JSONSchemaValidationError(message: String) extends CreateAccountError

    case class BackendError(message: String) extends CreateAccountError

  }

}
