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
import cats.syntax.either._
import cats.syntax.traverse._
import com.google.inject.Inject
import configs.syntax._
import play.api.{Application, Logger}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Request}
import play.api.mvc.Result
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig.personalAccountUrl
import uk.gov.hmrc.helptosavefrontend.connectors.SessionCacheConnector
import uk.gov.hmrc.helptosavefrontend.models.EligibilityCheckError._
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.services.{HelpToSaveService, JSONSchemaValidationService}
import uk.gov.hmrc.helptosavefrontend.util.{HTSAuditor, NINO}
import uk.gov.hmrc.helptosavefrontend.views
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class EligibilityCheckController  @Inject()(val messagesApi: MessagesApi,
                                            helpToSaveService: HelpToSaveService,
                                            sessionCacheConnector: SessionCacheConnector,
                                            jsonSchemaValidationService: JSONSchemaValidationService,
                                            app: Application,
                                            auditor: HTSAuditor)(implicit ec: ExecutionContext)
  extends HelpToSaveAuth(app) with I18nSupport {

  import EligibilityCheckController._


  private[controllers] val oauthConfig = app.configuration.underlying.get[OAuthConfiguration]("oauth").value

  def getAuthorisation: Action[AnyContent] = authorisedForHtsWithNino {
    implicit request ⇒
      implicit htsContext⇒
        Future.successful(redirectForAuthorisationCode(request, htsContext))
  }

  def confirmDetails(code: Option[String],
                     error: Option[String],
                     error_description: Option[String],
                     error_code: Option[String]): Action[AnyContent] =  authorisedForHtsWithNino {
    implicit request ⇒
      implicit htsContext ⇒
        (code, error) match {
          case (Some(authorisationCode), _) ⇒
            val result: EitherT[Future, EligibilityCheckError, (NINO, EligibilityCheckResult)] = for {
              nino ← EitherT.fromOption[Future](htsContext.nino, EligibilityCheckError.NoNINO)
              eligible ← helpToSaveService.checkEligibility(nino, authorisationCode)
              nsiUserInfo = eligible.result.map(NSIUserInfo(_))
              _ <- EitherT.fromEither[Future](validateCreateAccountJsonSchema(nsiUserInfo)).leftMap(e ⇒ JSONSchemaValidationError(e, nino))
              _ ← writeToKeyStore(nsiUserInfo).leftMap[EligibilityCheckError](e ⇒ KeyStoreWriteError(e, nino))
            } yield (nino, eligible)

            result.fold(
              handleEligibilityCheckError, { case (nino, eligibility) ⇒
                eligibility.result.fold {
                  auditor.sendEvent(new EligibilityCheckEvent(nino, Some("Unknown eligibility problem")))
                  SeeOther(routes.EligibilityCheckController.notEligible().url)
                } { info ⇒
                  auditor.sendEvent(new EligibilityCheckEvent(nino, None))
                  Ok(views.html.register.confirm_details(info))
                }

              })

          case (_, Some(e)) ⇒
            Logger.error(s"Could not get authorisation code: $e. Error description was" +
              s"${error_description.getOrElse("-")}, error code was ${error_code.getOrElse("-")}")
            // TODO: do something better
            Future.successful(InternalServerError(s"Could not get authorisation code: $e"))


          case _ ⇒
            // we should never reach here - we shouldn't have a successful code and an error at the same time
            Logger.error("Inconsistent result found when attempting to retrieve an authorisation code")
            Future.successful(InternalServerError(""))
        }
  }

  val notEligible: Action[AnyContent] = unprotected {
    implicit request ⇒
      implicit htsContext ⇒
        Future.successful(Ok(views.html.core.not_eligible()))
  }

  private def handleEligibilityCheckError(e: EligibilityCheckError)(
    implicit request: Request[AnyContent], hc: HeaderCarrier, htsContext: HtsContext
  ): Result = e match {
    case NoNINO =>
      Logger.error("Could not find NINO")
      InternalServerError

    case BackendError(message, nino) =>
      Logger.error(s"An error occured while trying to call the backend service for user $nino: $message")
      InternalServerError

    case MissingUserInfos(missingInfo, nino) =>
      val problemDescription = s"user $nino has missing information: ${missingInfo.mkString(",")}"
      Logger.error(problemDescription)
      auditor.sendEvent(new EligibilityCheckEvent(nino, Some(problemDescription)))
      Ok(views.html.register.missing_user_info(missingInfo, personalAccountUrl))

    case JSONSchemaValidationError(message, nino) =>
      Logger.error(s"JSON schema validation failed for user $nino: $message")
      InternalServerError

    case KeyStoreWriteError(message, nino) =>
      Logger.error(s"Could not write to key store for user $nino: $message")
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

  /**
    * Writes the user info to key-store if it exists and returns the associated [[CacheMap]]. If the user info
    * is not defined, don't do anything and return [[None]]. Any errors during writing to key-store are
    * captured as a [[String]] in the [[Either]].
    */
  private def writeToKeyStore(userDetails: Option[NSIUserInfo])(implicit hc: HeaderCarrier): EitherT[Future, String, Option[CacheMap]] = {
    // write to key-store
    val cacheMapOption: Option[Future[CacheMap]] =
      userDetails.map { details ⇒ sessionCacheConnector.put(HTSSession(Some(details))) }

    // use traverse to swap the option and future
    val cacheMapFuture: Future[Option[CacheMap]] =
      cacheMapOption.traverse[Future, CacheMap](identity)

    EitherT(
      cacheMapFuture.map[Either[String, Option[CacheMap]]](Right(_))
        .recover { case e ⇒ Left(e.getMessage) }
    )
  }

  private lazy val redirectForAuthorisationCode =
    if (oauthConfig.enabled) {
      { (_: Request[AnyContent], _: HtsContext) ⇒
        Logger.info("Received request to get user details: redirecting to oauth obtain authorisation code")

        // we need to get an authorisation token from OAuth - redirect to OAuth here. When the authorisation
        // is done they'll redirect to the callback url we give them
        Redirect(
          oauthConfig.url,
          Map(
            "client_id" -> Seq(oauthConfig.clientID),
            "scope" -> oauthConfig.scopes,
            "response_type" -> Seq("code"),
            "redirect_uri" -> Seq(oauthConfig.callbackURL)
          ))
      }
    } else {
      { (request: Request[AnyContent], htsContext: HtsContext) ⇒
        // if the redirect to oauth is not enabled redirect straight to our 'confirm-details' endpoint
        // using the NINO as the authorisation code
        implicit val r = request

        htsContext.nino.fold {
          Logger.error("NINO or user details URI not available")
          InternalServerError("")
        } { nino ⇒
          Logger.info(s"Received request to get user details: redirecting to get user details using NINO $nino as authorisation code")
          Redirect(routes.EligibilityCheckController.confirmDetails(Some(nino), None, None, None).absoluteURL())
        }
      }
    }
}

object EligibilityCheckController {

  // details required to get an authorisation token from OAuth
  private[controllers] case class OAuthConfiguration(enabled: Boolean, url: String, clientID: String, callbackURL: String, scopes: List[String])


}