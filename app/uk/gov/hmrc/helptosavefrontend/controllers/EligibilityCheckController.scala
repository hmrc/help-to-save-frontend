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

import cats.data.EitherT
import cats.instances.future._
import cats.instances.option._
import cats.syntax.eq._
import cats.syntax.traverse._
import com.google.inject.Inject
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Request, Result ⇒ PlayResult}
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig.personalTaxAccountUrl
import uk.gov.hmrc.helptosavefrontend.config.{FrontendAppConfig, FrontendAuthConnector}
import uk.gov.hmrc.helptosavefrontend.connectors.SessionCacheConnector
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityCheckResult.Ineligible
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.eligibility.{EligibilityCheckResult, IneligibilityType}
import uk.gov.hmrc.helptosavefrontend.models.userinfo.UserInfo
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.Logging._
import uk.gov.hmrc.helptosavefrontend.util.{Logging, Result, toFuture}
import uk.gov.hmrc.helptosavefrontend.views
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.config.AppName

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class EligibilityCheckController @Inject() (val messagesApi:           MessagesApi,
                                            val helpToSaveService:     HelpToSaveService,
                                            val sessionCacheConnector: SessionCacheConnector,
                                            auditor:                   HTSAuditor,
                                            frontendAuthConnector:     FrontendAuthConnector,
                                            metrics:                   Metrics)(implicit ec: ExecutionContext)
  extends HelpToSaveAuth(frontendAuthConnector, metrics) with EnrolmentCheckBehaviour with SessionBehaviour with I18nSupport with Logging with AppName {

  def getCheckEligibility: Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒
    checkIfAlreadyEnrolled({
      () ⇒
        checkSession {
          // there is no session yet
          getEligibilityActionResult()
        } { session ⇒
          // there is a session
          session.eligibilityCheckResult.fold(
            _ ⇒ SeeOther(routes.EligibilityCheckController.getIsNotEligible().url), // user is not eligible
            _ ⇒ SeeOther(routes.EligibilityCheckController.getIsEligible().url) // user is eligible
          )
        }
    }, { _ ⇒
      // if there is an error checking the enrolment, do the eligibility checks
      getEligibilityActionResult()
    }
    )
  }(redirectOnLoginURL = FrontendAppConfig.checkEligibilityUrl)

  // $COVERAGE-OFF$
  def getCheckEligibilityHack: Action[AnyContent] = authorisedForHtsWithInfoHack { implicit request ⇒ implicit htsContext ⇒
    checkIfAlreadyEnrolled({
      () ⇒
        checkSession {
          // there is no session yet
          getEligibilityActionResult()
        } { session ⇒
          // there is a session
          session.eligibilityCheckResult.fold(
            _ ⇒ SeeOther(routes.EligibilityCheckController.getIsNotEligible().url), // user is not eligible
            _ ⇒ SeeOther(routes.EligibilityCheckController.getIsEligible().url) // user is eligible
          )
        }
    }, { _ ⇒
      // if there is an error checking the enrolment, do the eligibility checks
      getEligibilityActionResult()
    }
    )
  }(redirectOnLoginURL = FrontendAppConfig.checkEligibilityUrl)
  // $COVERAGE-ON

  val getIsNotEligible: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    checkIfAlreadyEnrolled { () ⇒
      checkSession {
        SeeOther(routes.EligibilityCheckController.getCheckEligibility().url)
      } {
        _.eligibilityCheckResult.fold(
          { ineligible ⇒
            val ineligibilityType = IneligibilityType.fromIneligible(ineligible)
            if (ineligibilityType === IneligibilityType.Unknown) {
              logger.warn(s"Could not parse ineligibility reason: reason code was ${ineligible.value.reasonCode} " +
                s"and reason description was ${ineligible.value.reason}")
            }

            Ok(views.html.core.not_eligible(ineligibilityType))
          },
          _ ⇒ SeeOther(routes.EligibilityCheckController.getIsEligible().url)
        )
      }
    }
  }(redirectOnLoginURL = FrontendAppConfig.checkEligibilityUrl)

  val getIsEligible: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    checkIfAlreadyEnrolled { () ⇒
      checkSession {
        SeeOther(routes.EligibilityCheckController.getCheckEligibility().url)
      } {
        _.eligibilityCheckResult.fold(
          _ ⇒ SeeOther(routes.EligibilityCheckController.getIsNotEligible().url),
          userInfo ⇒ Ok(views.html.register.you_are_eligible(userInfo))
        )
      }
    }
  }(redirectOnLoginURL = FrontendAppConfig.checkEligibilityUrl)

  private def getEligibilityActionResult()(implicit hc: HeaderCarrier,
                                           htsContext: HtsContextWithNINOAndUserDetails,
                                           request:    Request[AnyContent]): Future[PlayResult] = {
    htsContext.userDetails.fold[Future[PlayResult]](
      { missingUserInfo ⇒
        logger.warn(s"User has missing information: ${missingUserInfo.missingInfo.mkString(",")}", missingUserInfo.nino)
        Ok(views.html.register.missing_user_info(missingUserInfo.missingInfo, personalTaxAccountUrl))
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
      htsContext: HtsContextWithNINOAndUserDetails): EitherT[Future, String, EligibilityCheckResult] =
    for {
      eligible ← helpToSaveService.checkEligibility()
      session = {
        val result = eligible.fold[Option[Either[Ineligible, UserInfo]]](
          _ ⇒ Some(Right(userInfo)),
          ineligible ⇒ Some(Left(Ineligible(ineligible))),
          _ ⇒ None)
        result.map(r ⇒ HTSSession(r, None))
      }
      _ ← session.map(sessionCacheConnector.put).traverse[Result, CacheMap](identity)
    } yield eligible

  private def handleEligibilityResult(result: EligibilityCheckResult)(implicit htsContext: HtsContextWithNINOAndUserDetails, hc: HeaderCarrier): PlayResult = {
    val nino = htsContext.nino
    auditor.sendEvent(EligibilityResultEvent(nino, result), nino)
    result.fold(
      _ ⇒ SeeOther(routes.EligibilityCheckController.getIsEligible().url),
      _ ⇒ SeeOther(routes.EligibilityCheckController.getIsNotEligible().url),
      _ ⇒ {
        // set the ITMP flag here but don't worry about the result
        helpToSaveService.setITMPFlag().value.onComplete {
          case Failure(e)        ⇒ logger.warn(s"Could not set ITMP flag, future failed: ${e.getMessage}", nino)
          case Success(Left(e))  ⇒ logger.warn(s"Could not set ITMP flag: $e", nino)
          case Success(Right(_)) ⇒ logger.info(s"Successfully set ITMP flag for user", nino)
        }

        SeeOther(FrontendAppConfig.nsiManageAccountUrl)
      }
    )
  }

}

