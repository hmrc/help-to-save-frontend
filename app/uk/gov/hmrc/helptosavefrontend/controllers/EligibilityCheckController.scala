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
import cats.syntax.either._
import com.google.inject.Inject
import play.api.Application
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig.personalAccountUrl
import uk.gov.hmrc.helptosavefrontend.config.{FrontendAppConfig, FrontendAuthConnector}
import uk.gov.hmrc.helptosavefrontend.connectors.SessionCacheConnector
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.models.{MissingUserInfos, _}
import uk.gov.hmrc.helptosavefrontend.services.{HelpToSaveService, JSONSchemaValidationService}
import uk.gov.hmrc.helptosavefrontend.util.Logging._
import uk.gov.hmrc.helptosavefrontend.util.{Logging, toFuture}
import uk.gov.hmrc.helptosavefrontend.views
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.config.AppName

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class EligibilityCheckController @Inject() (val messagesApi:             MessagesApi,
                                            val helpToSaveService:       HelpToSaveService,
                                            val sessionCacheConnector:   SessionCacheConnector,
                                            jsonSchemaValidationService: JSONSchemaValidationService,
                                            val app:                     Application,
                                            auditor:                     HTSAuditor,
                                            frontendAuthConnector:       FrontendAuthConnector,
                                            metrics:                     Metrics)(implicit ec: ExecutionContext)
  extends HelpToSaveAuth(frontendAuthConnector, metrics) with EnrolmentCheckBehaviour with SessionBehaviour with I18nSupport with Logging with AppName {

  import EligibilityCheckController._

  def getCheckEligibility: Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒
    checkIfAlreadyEnrolled({
      () ⇒
        checkSession {
          // there is no session yet
          getEligibilityActionResult()
        } { session ⇒
          // there is a session
          session.eligibilityCheckResult.fold(
            // user is not eligible
            SeeOther(routes.EligibilityCheckController.getIsNotEligible().url)
          )(_ ⇒
              // user is eligible
              SeeOther(routes.EligibilityCheckController.getIsEligible().url)
            )
        }
    }, { _ ⇒
      // if there is an error checking the enrolment, do the eligibility checks
      getEligibilityActionResult()
    }
    )
  }(redirectOnLoginURL = FrontendAppConfig.checkEligibilityUrl)

  val getIsNotEligible: Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒
    checkIfAlreadyEnrolled { () ⇒
      checkSession {
        SeeOther(routes.EligibilityCheckController.getCheckEligibility().url)
      } {
        _.eligibilityCheckResult.fold {
          Ok(views.html.core.not_eligible())
        }(_ ⇒
          SeeOther(routes.EligibilityCheckController.getIsEligible().url)
        )
      }
    }
  }(redirectOnLoginURL = FrontendAppConfig.checkEligibilityUrl)

  val getIsEligible: Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒
    checkIfAlreadyEnrolled { () ⇒
      checkSession {
        SeeOther(routes.EligibilityCheckController.getCheckEligibility().url)
      } {
        _.eligibilityCheckResult.fold(
          SeeOther(routes.EligibilityCheckController.getIsNotEligible().url)
        )(_ ⇒
            Ok(views.html.register.you_are_eligible())
          )
      }
    }
  }(redirectOnLoginURL = FrontendAppConfig.checkEligibilityUrl)

  private def getEligibilityActionResult()(implicit hc: HeaderCarrier,
                                           htsContext: HtsContextWithNINO,
                                           request:    Request[AnyContent]): Future[Result] =
    performEligibilityChecks().fold(
      e ⇒ handleEligibilityCheckError(e),
      r ⇒ handleEligibilityResult(r))

  private def performEligibilityChecks()(implicit hc: HeaderCarrier, htsContext: HtsContextWithNINO): EitherT[Future, Error, EligibilityCheckResult] =
    for {
      nsiUserInfo ← getUserInformation()
      eligible ← helpToSaveService.checkEligibility().leftMap(Error.apply)
      session = {
        val maybeUserInfo = eligible.fold(_ ⇒ Some(nsiUserInfo), _ ⇒ None, _ ⇒ None)
        HTSSession(maybeUserInfo, None)
      }
      _ ← EitherT.fromEither[Future](validateCreateAccountJsonSchema(session.eligibilityCheckResult)).leftMap(Error.apply)
      _ ← sessionCacheConnector.put(session).leftMap[Error](Error.apply)
    } yield eligible

  private def getUserInformation()(implicit htsContext: HtsContextWithNINO): EitherT[Future, Error, NSIUserInfo] =
    EitherT.fromEither[Future](htsContext.userDetails.leftMap(missingInfo ⇒ Error(missingInfo)))

  private def handleEligibilityResult(result: EligibilityCheckResult)(implicit htsContext: HtsContextWithNINO, hc: HeaderCarrier): Result = {
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

        SeeOther(routes.NSIController.goToNSI().url)
      }
    )
  }

  private def handleEligibilityCheckError(error: Error)(implicit request: Request[AnyContent],
                                                        hc:         HeaderCarrier,
                                                        htsContext: HtsContextWithNINO): Result = error.value match {
    case Left(e) ⇒
      logger.warn(e, htsContext.nino)
      internalServerError()

    case Right(missingUserInfo) ⇒
      logger.warn(s"User has missing information: ${missingUserInfo.missingInfo.mkString(",")}", missingUserInfo.nino)
      Ok(views.html.register.missing_user_info(missingUserInfo.missingInfo, personalAccountUrl))
  }

  private def validateCreateAccountJsonSchema(userInfo: Option[NSIUserInfo]): Either[String, Unit] = {
    import uk.gov.hmrc.helptosavefrontend.util.Toggles._

    userInfo.fold[Either[String, Unit]](Right(())) { userInfo ⇒
      FEATURE("create-account-json-validation", app.configuration, logger, Some(userInfo.nino)).thenOrElse(
        jsonSchemaValidationService.validate(Json.toJson(userInfo)).map(_ ⇒ {
        }),
        Right(()
        )
      )
    }
  }

}

object EligibilityCheckController {

  private case class Error(value: Either[String, MissingUserInfos])

  private object Error {
    def apply(error: String): Error = Error(Left(error))

    def apply(u: MissingUserInfos): Error = Error(Right(u))
  }
}
