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
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig.personalAccountUrl
import uk.gov.hmrc.helptosavefrontend.connectors.SessionCacheConnector
import uk.gov.hmrc.helptosavefrontend.models.EligibilityCheckError._
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.services.{EnrolmentService, HelpToSaveService, JSONSchemaValidationService}
import uk.gov.hmrc.helptosavefrontend.util.{HTSAuditor, Logging, NINO}
import uk.gov.hmrc.helptosavefrontend.util.toFuture
import uk.gov.hmrc.helptosavefrontend.views
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class EligibilityCheckController  @Inject()(val messagesApi: MessagesApi,
                                            helpToSaveService: HelpToSaveService,
                                            val sessionCacheConnector: SessionCacheConnector,
                                            jsonSchemaValidationService: JSONSchemaValidationService,
                                            val enrolmentService: EnrolmentService,
                                            val app: Application,
                                            auditor: HTSAuditor)(implicit ec: ExecutionContext)
  extends HelpToSaveAuth(app) with EnrolmentCheckBehaviour with SessionBehaviour with I18nSupport with Logging {

  def getCheckEligibility: Action[AnyContent] =  authorisedForHtsWithInfo {
    implicit request ⇒
      implicit htsContext ⇒
        checkIfAlreadyEnrolled{ nino ⇒
          checkSession(
            // there is no session yet
            performEligibilityChecks(nino, htsContext).fold(
              e ⇒ handleEligibilityCheckError(e),
              { case (nino, eligibility) ⇒ handleEligibilityResult(eligibility, nino) }
            ), session ⇒
              // there is a session
              handleEligibilityResult(session, nino)
          )
        }
  }

  val notEligible: Action[AnyContent] = authorisedForHtsWithInfo {
    implicit request ⇒
      implicit htsContext ⇒
        checkIfAlreadyEnrolled { _ ⇒
          checkSession(
            SeeOther(routes.IntroductionController.getApply().url),
            _.eligibilityCheckResult.fold {
              Ok(views.html.core.not_eligible())
            }(_ ⇒
              SeeOther(routes.EligibilityCheckController.getIsEligible().url)
            )
          )
        }
  }

  val getIsEligible: Action[AnyContent] = authorisedForHtsWithInfo {
    implicit request ⇒
      implicit htsContext ⇒
        checkIfAlreadyEnrolled { _ ⇒
          checkSession(
            SeeOther(routes.IntroductionController.getApply().url),
            _.eligibilityCheckResult.fold(
              SeeOther(routes.EligibilityCheckController.notEligible().url)
            )(_ ⇒
              Ok(views.html.register.you_are_eligible())
            )
          )
        }
  }

  private def performEligibilityChecks(nino: NINO,
                                       htsContext: HtsContext
                                      )(implicit hc: HeaderCarrier): EitherT[Future, EligibilityCheckError, (NINO, HTSSession)] =
    for {
      userDetailsURI ← EitherT.fromOption[Future](htsContext.userDetailsURI, EligibilityCheckError.NoUserDetailsURI(nino))
      eligible       ← helpToSaveService.checkEligibility(nino, userDetailsURI)
      nsiUserInfo    = eligible.result.map(NSIUserInfo(_))
      _              ← EitherT.fromEither[Future](validateCreateAccountJsonSchema(nsiUserInfo)).leftMap(e ⇒ JSONSchemaValidationError(e, nino))
      session        = HTSSession(nsiUserInfo)
      _              ←  sessionCacheConnector.put(session).leftMap[EligibilityCheckError](e ⇒ KeyStoreWriteError(e, nino))
    } yield (nino, session)

  private def handleEligibilityResult(eligibilityCheckResult: HTSSession,
                                      nino: NINO
                                     )(implicit hc: HeaderCarrier) = {
    eligibilityCheckResult.eligibilityCheckResult.fold{
      auditor.sendEvent(new EligibilityCheckEvent(nino, Some("Unknown eligibility problem")))
      SeeOther(routes.EligibilityCheckController.notEligible().url)
    } { _ ⇒
      auditor.sendEvent(new EligibilityCheckEvent(nino, None))
      SeeOther(routes.EligibilityCheckController.getIsEligible().url)
    }
  }

  private def handleEligibilityCheckError(e: EligibilityCheckError)(
    implicit request: Request[AnyContent], hc: HeaderCarrier, htsContext: HtsContext
  ): Result = e match {
    case NoUserDetailsURI(nino) ⇒
      logger.warn(s"Could not find user details URI for user $nino")
      InternalServerError

    case BackendError(message, nino) =>
      logger.warn(s"An error occurred while trying to call the backend service for user $nino: $message")
      InternalServerError

    case MissingUserInfos(missingInfo, nino) =>
      val problemDescription = s"user $nino has missing information: ${missingInfo.mkString(",")}"
      logger.warn(problemDescription)
      auditor.sendEvent(new EligibilityCheckEvent(nino, Some(problemDescription)))
      Ok(views.html.register.missing_user_info(missingInfo, personalAccountUrl))

    case JSONSchemaValidationError(message, nino) =>
      logger.warn(s"JSON schema validation failed for user $nino: $message")
      InternalServerError

    case KeyStoreWriteError(message, nino) =>
      logger.error(s"Could not write to key store for user $nino: $message")
      InternalServerError
  }

  private def validateCreateAccountJsonSchema(userInfo: Option[NSIUserInfo]): Either[String, Option[NSIUserInfo]] = {
    import uk.gov.hmrc.helptosavefrontend.util.Toggles._

    userInfo match {
      case None => Right(None)
      case Some(ui) =>
        FEATURE[Either[String, Option[NSIUserInfo]]]("outgoing-json-validation", app.configuration, Right(userInfo)) enabled() thenDo {
          jsonSchemaValidationService.validate(Json.toJson(ui)).map(_ ⇒ Some(ui))
        }
    }
  }

}

