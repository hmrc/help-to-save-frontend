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
import play.api.Application
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig.personalAccountUrl
import uk.gov.hmrc.helptosavefrontend.connectors.SessionCacheConnector
import uk.gov.hmrc.helptosavefrontend.enrolment.EnrolmentStore
import uk.gov.hmrc.helptosavefrontend.models.EligibilityCheckError._
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.services.{EnrolmentService, HelpToSaveService, JSONSchemaValidationService}
import uk.gov.hmrc.helptosavefrontend.util.{HTSAuditor, Logging, NINO}
import uk.gov.hmrc.helptosavefrontend.views
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class EligibilityCheckController  @Inject()(val messagesApi: MessagesApi,
                                            helpToSaveService: HelpToSaveService,
                                            sessionCacheConnector: SessionCacheConnector,
                                            jsonSchemaValidationService: JSONSchemaValidationService,
                                            enrolmentService: EnrolmentService,
                                            val app: Application,
                                            auditor: HTSAuditor)(implicit ec: ExecutionContext)
  extends HelpToSaveAuth(app) with I18nSupport with Logging {

  import EligibilityCheckController.EligibilityResult

  def getCheckEligibility: Action[AnyContent] =  authorisedForHtsWithInfo  {
    implicit request ⇒
      implicit htsContext ⇒
        val eligibilityCheck = for {
          nino            ← EitherT.fromOption[Future](htsContext.nino, NoNINO)
          enrolmentStatus ← enrolmentService.getUserEnrolmentStatus(nino).leftMap(e ⇒ EnrolmentCheckError(nino, e))
          result          ← handleEnrolmentStatus(enrolmentStatus, nino, htsContext)
        } yield result

        eligibilityCheck.fold(
          handleEligibilityCheckError, {
            case EligibilityResult(nino, Left(EnrolmentStore.Enrolled(itmpHtSFlag))) ⇒
              // if the user is enrolled but the itmp flag is not set then just
              // start the process to set the itmp flag here without worrying about the result
              if (!itmpHtSFlag){
                enrolmentService.setITMPFlag(nino).fold(
                  e ⇒ logger.warn(s"Could not start process to set ITMP flag for user $nino: $e"),
                  _ ⇒ logger.info(s"Process started to set ITMP flag for user $nino")
                )
              }
              Ok("You've already got an account - yay!")

            case EligibilityResult(nino, Right(eligibility)) ⇒
              // user wasn't already enrolled and the eligibility check was successful
              eligibility.result.fold {
                auditor.sendEvent(new EligibilityCheckEvent(nino, Some("Unknown eligibility problem")))
                SeeOther(routes.EligibilityCheckController.notEligible().url)
              } { _ ⇒
                auditor.sendEvent(new EligibilityCheckEvent(nino, None))
                SeeOther(routes.EligibilityCheckController.getIsEligible().url)
              }

          })
  }

  val notEligible: Action[AnyContent] = unprotected {
    implicit request ⇒
      implicit htsContext ⇒
        Future.successful(Ok(views.html.core.not_eligible()))
  }

  val getIsEligible: Action[AnyContent] = authorisedForHtsWithInfo {
    implicit request ⇒
      implicit htsContext ⇒
        Future.successful(Ok(views.html.register.you_are_eligible()))
  }

  /**
    * If the user is enrolled return None. If the user is not enrolled perform the
    * eligibility checks and return Some if successful
    */
  private def handleEnrolmentStatus(enrolmentStatus: EnrolmentStore.Status,
                                    nino: NINO,
                                    htsContext: HtsContext)(implicit hc: HeaderCarrier): EitherT[Future, EligibilityCheckError, EligibilityResult] =
    enrolmentStatus.fold(
      performAndHandleEligibilityChecks(nino, htsContext),
      itmpFlag ⇒ EitherT.pure(EligibilityResult(nino, Left(EnrolmentStore.Enrolled(itmpFlag))))
    )

  private def performAndHandleEligibilityChecks(nino: NINO,
                                                htsContext: HtsContext)(implicit hc: HeaderCarrier): EitherT[Future, EligibilityCheckError, EligibilityResult] =
    for {
      userDetailsURI ← EitherT.fromOption[Future](htsContext.userDetailsURI, EligibilityCheckError.NoUserDetailsURI(nino))
      eligible ← helpToSaveService.checkEligibility(nino, userDetailsURI)
      nsiUserInfo = eligible.result.map(NSIUserInfo(_))
      _ <- EitherT.fromEither[Future](validateCreateAccountJsonSchema(nsiUserInfo)).leftMap(e ⇒ JSONSchemaValidationError(e, nino))
      _ ← writeToKeyStore(nsiUserInfo).leftMap[EligibilityCheckError](e ⇒ KeyStoreWriteError(e, nino))
    } yield EligibilityResult(nino, Right(eligible))

  private def handleEligibilityCheckError(e: EligibilityCheckError)(
    implicit request: Request[AnyContent], hc: HeaderCarrier, htsContext: HtsContext
  ): Result = e match {
    case NoNINO =>
      logger.warn("Could not find NINO")
      InternalServerError

    case EnrolmentCheckError(nino, message) ⇒
      logger.warn(s"Error checking if user was enrolled for user $nino: $message")
      InternalServerError

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
}

object EligibilityCheckController {

  /**
    * Contains the result of checking eligibility
    *
    * @param nino The NINO of the user
    * @param result Left if the user is already enrolled and Right if the user is not
    *               enrolled and the eligibility checks were successful
    */
  private case class EligibilityResult(nino: NINO, result: Either[EnrolmentStore.Enrolled,EligibilityCheckResult])

}
