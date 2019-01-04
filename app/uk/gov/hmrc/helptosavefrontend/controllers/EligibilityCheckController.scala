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

import cats.data.EitherT
import cats.instances.future._
import cats.instances.option._
import cats.syntax.traverse._
import com.google.inject.Inject
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent, Request, Result ⇒ PlayResult}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosavefrontend.auth.HelpToSaveAuth
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.controllers.SessionBehaviour.SessionWithEligibilityCheck
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.models.HTSSession.EligibleWithUserInfo
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityCheckResultType.{Eligible, Ineligible}
import uk.gov.hmrc.helptosavefrontend.models.eligibility.{EligibilityCheckResultType, IneligibilityReason}
import uk.gov.hmrc.helptosavefrontend.models.userinfo.UserInfo
import uk.gov.hmrc.helptosavefrontend.repo.SessionStore
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.Logging._
import uk.gov.hmrc.helptosavefrontend.util.{NINOLogMessageTransformer, Result, toFuture}
import uk.gov.hmrc.helptosavefrontend.views
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class EligibilityCheckController @Inject() (val helpToSaveService: HelpToSaveService,
                                            val sessionStore:      SessionStore,
                                            val authConnector:     AuthConnector,
                                            val metrics:           Metrics)(implicit override val messagesApi: MessagesApi,
                                                                            val transformer:       NINOLogMessageTransformer,
                                                                            val frontendAppConfig: FrontendAppConfig,
                                                                            val config:            Configuration,
                                                                            val env:               Environment,
                                                                            ec:                    ExecutionContext)
  extends BaseController with HelpToSaveAuth with EnrolmentCheckBehaviour with SessionBehaviour with CapCheckBehaviour {

  val earlyCapCheckOn: Boolean = frontendAppConfig.getBoolean("enable-early-cap-check")

  def getCheckEligibility: Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒
    def determineEligibility =
        checkSession {
          // there is no session yet
          getEligibilityActionResult()
        } { session ⇒
          // there is a session
          session.eligibilityCheckResult.fold(getEligibilityActionResult()) {
            _.fold(
              _ ⇒ SeeOther(routes.EligibilityCheckController.getIsNotEligible().url), // user is not eligible
              _ ⇒ SeeOther(routes.EligibilityCheckController.getIsEligible().url) // user is eligible
            )
          }
        }

    checkIfAlreadyEnrolled(
      () ⇒ if (earlyCapCheckOn) {
        logger.info("Checking pre-eligibility cap for nino", htsContext.nino)
        checkIfAccountCreateAllowed(determineEligibility)
      } else {
        determineEligibility
      },
      { _ ⇒
        // if there is an error checking the enrolment, do the eligibility checks
        if (earlyCapCheckOn) {
          logger.info("Checking pre-eligibility cap for nino", htsContext.nino)
          checkIfAccountCreateAllowed(getEligibilityActionResult())
        } else {
          getEligibilityActionResult
        }
      })
  }(redirectOnLoginURL = routes.EligibilityCheckController.getCheckEligibility().url)

  val getIsNotEligible: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    checkIfAlreadyEnrolled { () ⇒
      checkHasDoneEligibilityChecks {
        SeeOther(routes.EligibilityCheckController.getCheckEligibility().url)
      } {
        _.eligibilityResult.fold(
          { ineligibleReason ⇒
            val ineligibilityType = IneligibilityReason.fromIneligible(ineligibleReason)
            val threshold = ineligibleReason.value.threshold
            ineligibilityType.fold {
              logger.warn(s"Could not parse ineligibility reason: $ineligibleReason", htsContext.nino)
              internalServerError()
            } { i ⇒
              Ok(views.html.register.not_eligible(i, threshold))
            }
          },
          _ ⇒ SeeOther(routes.EligibilityCheckController.getIsEligible().url)
        )
      }
    }
  }(redirectOnLoginURL = routes.EligibilityCheckController.getIsNotEligible().url)

  val getIsEligible: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    checkIfAlreadyEnrolled { () ⇒
      checkHasDoneEligibilityChecks {
        SeeOther(routes.EligibilityCheckController.getCheckEligibility().url)
      } {
        _.eligibilityResult.fold(
          _ ⇒ SeeOther(routes.EligibilityCheckController.getIsNotEligible().url),
          eligibleWithUserInfo ⇒ Ok(views.html.register.you_are_eligible(eligibleWithUserInfo.userInfo))
        )
      }
    }
  }(redirectOnLoginURL = frontendAppConfig.checkEligibilityUrl)

  val youAreEligibleSubmit: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    checkHasDoneEligibilityChecks {
      SeeOther(routes.EligibilityCheckController.getCheckEligibility().url)
    } {
      _.eligibilityResult.fold(
        _ ⇒ SeeOther(routes.EligibilityCheckController.getIsNotEligible().url),
        { userInfo ⇒
          val url = userInfo.userInfo.email.fold(
            routes.EmailController.getGiveEmailPage()
          )(_ ⇒ routes.EmailController.getSelectEmailPage()).url
          SeeOther(url)
        })
    }
  }(redirectOnLoginURL = routes.EligibilityCheckController.youAreEligibleSubmit().url)

  val getMissingInfoPage: Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒
    htsContext.userDetails.fold(
      missingInfo ⇒ Ok(views.html.register.missing_user_info(missingInfo.missingInfo)),
      _ ⇒ SeeOther(routes.EligibilityCheckController.getCheckEligibility().url)
    )
  }(redirectOnLoginURL = routes.EligibilityCheckController.getCheckEligibility().url)

  val getThinkYouAreEligiblePage: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    checkHasDoneEligibilityChecks {
      SeeOther(routes.EligibilityCheckController.getCheckEligibility().url)
    } {
      _.eligibilityResult.fold(
        _ ⇒ Ok(views.html.register.think_you_are_eligible()),
        _ ⇒ SeeOther(routes.EligibilityCheckController.getIsEligible().url)
      )
    }
  }(redirectOnLoginURL = routes.EligibilityCheckController.getThinkYouAreEligiblePage().url)

  private def getEligibilityActionResult()(implicit hc: HeaderCarrier,
                                           htsContext: HtsContextWithNINOAndUserDetails,
                                           request:    Request[AnyContent]): Future[PlayResult] = {
    htsContext.userDetails.fold[Future[PlayResult]](
      { missingUserInfo ⇒
        logger.warn(s"User has missing information: ${missingUserInfo.missingInfo.mkString(",")}", missingUserInfo.nino)
        SeeOther(routes.EligibilityCheckController.getMissingInfoPage().url)
      }, { userInfo ⇒
        performEligibilityChecks(userInfo).fold(
          { e ⇒
            logger.warn(e, htsContext.nino)
            internalServerError()
          }, handleEligibilityResult
        )
      }
    )
  }

  private def performEligibilityChecks(userInfo: UserInfo)(
      implicit
      hc:         HeaderCarrier,
      htsContext: HtsContextWithNINOAndUserDetails): EitherT[Future, String, EligibilityCheckResultType] =
    for {
      eligible ← helpToSaveService.checkEligibility()
      session = {
        val result = eligible.fold[Option[Either[Ineligible, EligibleWithUserInfo]]](
          e ⇒ Some(Right(EligibleWithUserInfo(Eligible(e), userInfo))),
          ineligible ⇒ Some(Left(Ineligible(ineligible))),
          _ ⇒ None)
        result.map(r ⇒ HTSSession(Some(r), None, None))
      }
      _ ← session.map(sessionStore.store).traverse[Result, Unit](identity)
    } yield eligible

  private def handleEligibilityResult(result: EligibilityCheckResultType)(implicit htsContext: HtsContextWithNINOAndUserDetails, hc: HeaderCarrier): PlayResult = {
    val nino = htsContext.nino
    result.fold(
      _ ⇒ SeeOther(routes.EligibilityCheckController.getIsEligible().url),
      _ ⇒ SeeOther(routes.EligibilityCheckController.getIsNotEligible().url),
      _ ⇒ {
        helpToSaveService.setITMPFlagAndUpdateMongo().value.onComplete {
          case Failure(e)        ⇒ logger.warn(s"error in setting ITMP flag and updating mongo, future failed: ${e.getMessage}", nino)
          case Success(Left(e))  ⇒ logger.warn(s"error in setting ITMP flag and updating mongo: $e", nino)
          case Success(Right(_)) ⇒ logger.info(s"Successfully set ITMP flag and updated mongo for user", nino)
        }

        SeeOther(frontendAppConfig.nsiManageAccountUrl)
      }
    )
  }

  private def checkHasDoneEligibilityChecks(noSession: ⇒ Future[PlayResult])(hasDoneChecks: SessionWithEligibilityCheck ⇒ Future[PlayResult])(
      implicit
      htsContext:  HtsContextWithNINO,
      hc:          HeaderCarrier,
      request:     Request[AnyContent],
      transformer: NINOLogMessageTransformer): Future[PlayResult] =
    checkSession(noSession){ session ⇒
      session.eligibilityCheckResult.fold[Future[PlayResult]](SeeOther(routes.EligibilityCheckController.getCheckEligibility().url))(
        result ⇒ hasDoneChecks(SessionWithEligibilityCheck(result, session.pendingEmail, session.confirmedEmail)))
    }

}

