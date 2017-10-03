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
import uk.gov.hmrc.helptosavefrontend.models.MissingUserInfos
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.services.{HelpToSaveService, JSONSchemaValidationService}
import uk.gov.hmrc.helptosavefrontend.util.{Logging, NINO, toFuture}
import uk.gov.hmrc.helptosavefrontend.views
import uk.gov.hmrc.play.config.AppName
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class EligibilityCheckController @Inject() (val messagesApi:             MessagesApi,
                                            val helpToSaveService:       HelpToSaveService,
                                            val sessionCacheConnector:   SessionCacheConnector,
                                            jsonSchemaValidationService: JSONSchemaValidationService,
                                            val app:                     Application,
                                            auditor:                     HTSAuditor,
                                            frontendAuthConnector:       FrontendAuthConnector)(implicit ec: ExecutionContext)
  extends HelpToSaveAuth(frontendAuthConnector) with EnrolmentCheckBehaviour with SessionBehaviour with I18nSupport with Logging with AppName {

  import EligibilityCheckController._

  def getCheckEligibility: Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒
    checkIfAlreadyEnrolled({
      nino ⇒
        checkSession {
          // there is no session yet
          getEligibilityActionResult(nino)
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
    }, { enrolmentCheckError ⇒
      // if there is an error checking the enrolment, do the eligibility checks
      getEligibilityActionResult(enrolmentCheckError.nino)
    }
    )
  }(redirectOnLoginURL = FrontendAppConfig.checkEligibilityUrl)

  val getIsNotEligible: Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒
    checkIfAlreadyEnrolled { _ ⇒
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
    checkIfAlreadyEnrolled { _ ⇒
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

  private def getEligibilityActionResult(nino: NINO)(implicit hc: HeaderCarrier,
                                                     htsContext: HtsContext,
                                                     request:    Request[AnyContent]): Future[Result] =
    performEligibilityChecks().fold(
      handleEligibilityCheckError,
      r ⇒ handleEligibilityResult(r, nino))

  private def performEligibilityChecks()(implicit hc: HeaderCarrier, htsContext: HtsContext): EitherT[Future, Error, EligibilityResultWithUserInfo] =
    for {
      nsiUserInfo ← getUserInformation()
      eligible ← helpToSaveService.checkEligibility().leftMap(Error.apply)
      _ ← EitherT.fromEither[Future](validateCreateAccountJsonSchema(eligible, nsiUserInfo)).leftMap(Error.apply)
      session = {
        val maybeUserInfo = eligible.result.fold(_ ⇒ None, _ ⇒ Some(nsiUserInfo))
        HTSSession(maybeUserInfo, None)
      }
      _ ← sessionCacheConnector.put(session).leftMap[Error](Error.apply)
    } yield EligibilityResultWithUserInfo(eligible.result.map(e ⇒ e -> nsiUserInfo))

  private def getUserInformation()(implicit htsContext: HtsContext): EitherT[Future, Error, NSIUserInfo] =
    EitherT.fromEither[Future](htsContext.userDetails.fold[Either[Error, NSIUserInfo]](
      Left(Error("unexpected error: Userinfo expected but not found"))) { info ⇒
        info.leftMap(missingInfo ⇒ Error(missingInfo))
      }
    )

  private def handleEligibilityResult(result: EligibilityResultWithUserInfo,
                                      nino:   NINO
  )(implicit hc: HeaderCarrier): Result = {
    result.value.fold(
      {
        case IneligibilityReason.AccountAlreadyOpened ⇒
          auditor.sendEvent(EligibilityCheckEvent(appName, nino, Some(IneligibilityReason.AccountAlreadyOpened.legibleString)))

          // set the ITMP flag here but don't worry about the result
          helpToSaveService.setITMPFlag().value.onComplete{
            case Failure(e)        ⇒ logger.warn(s"For NINO [$nino]: Could not set ITMP flag, future failed: ${e.getMessage}")
            case Success(Left(e))  ⇒ logger.warn(s"For NINO [$nino]: Could not set ITMP flag: $e")
            case Success(Right(_)) ⇒ logger.info(s"For NINO [$nino]: Successfully set ITMP flag for user")
          }

          Ok("You've already got an account - yay!!!")

        case other ⇒
          auditor.sendEvent(EligibilityCheckEvent(appName, nino, Some(other.legibleString)))
          SeeOther(routes.EligibilityCheckController.getIsNotEligible().url)
      }, {
        case (eligibilityReason, nsiUserInfo) ⇒
          auditor.sendEvent(EligibilityCheckEvent(appName, nino, None))
          SeeOther(routes.EligibilityCheckController.getIsEligible().url)
      })
  }

  private def handleEligibilityCheckError(error: Error)(implicit request: Request[AnyContent],
                                                        hc:         HeaderCarrier,
                                                        htsContext: HtsContext): Result = error.value match {
    case Left(e) ⇒
      logger.warn(e)
      InternalServerError

    case Right(missingUserInfo) ⇒
      val problemDescription = s"user ${missingUserInfo.nino} has missing information: ${missingUserInfo.missingInfo.mkString(",")}"
      logger.warn(problemDescription)
      auditor.sendEvent(EligibilityCheckEvent(appName, missingUserInfo.nino, Some(problemDescription)))
      Ok(views.html.register.missing_user_info(missingUserInfo.missingInfo, personalAccountUrl))
  }

  private def validateCreateAccountJsonSchema(eligibilityCheckResult: EligibilityCheckResult,
                                              userInfo:               NSIUserInfo): Either[String, NSIUserInfo] = {
    import uk.gov.hmrc.helptosavefrontend.util.Toggles._

    eligibilityCheckResult.result.fold(
      _ ⇒ Right(userInfo),
      _ ⇒ {
        FEATURE("outgoing-json-validation", app.configuration, logger).thenOrElse(
          jsonSchemaValidationService.validate(Json.toJson(userInfo)).map(_ ⇒ userInfo),
          Right(userInfo)
        )
      }
    )
  }

}

object EligibilityCheckController {

  private case class Error(value: Either[String, MissingUserInfos])

  private object Error {
    def apply(error: String): Error = Error(Left(error))

    def apply(u: MissingUserInfos): Error = Error(Right(u))
  }

  private case class EligibilityResultWithUserInfo(value: Either[IneligibilityReason, (EligibilityReason, NSIUserInfo)])

}
