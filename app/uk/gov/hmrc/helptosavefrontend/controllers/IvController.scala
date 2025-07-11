/*
 * Copyright 2023 HM Revenue & Customs
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

import cats.instances.future._
import com.google.inject.{Inject, Singleton}
import play.api.mvc._
import play.api.{Configuration, Environment}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosavefrontend.auth.HelpToSaveAuth
import uk.gov.hmrc.helptosavefrontend.config.{ErrorHandler, FrontendAppConfig}
import uk.gov.hmrc.helptosavefrontend.connectors.IvConnector
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.models.HTSSession
import uk.gov.hmrc.helptosavefrontend.models.iv.IvSuccessResponse._
import uk.gov.hmrc.helptosavefrontend.models.iv.JourneyId
import uk.gov.hmrc.helptosavefrontend.repo.SessionStore
import uk.gov.hmrc.helptosavefrontend.util.{MaintenanceSchedule, NINOLogMessageTransformer, toFuture}
import uk.gov.hmrc.helptosavefrontend.views.html.iv._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl.idFunctor
import uk.gov.hmrc.play.bootstrap.binders.{OnlyRelative, RedirectUrl}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class IvController @Inject() (
  val sessionStore: SessionStore,
  ivConnector: IvConnector,
  val authConnector: AuthConnector,
  val metrics: Metrics,
  cpd: CommonPlayDependencies,
  mcc: MessagesControllerComponents,
  errorHandler: ErrorHandler,
  maintenanceSchedule: MaintenanceSchedule,
  ivSuccessView: iv_success,
  failedIVview: failed_iv,
  insufficientEvidenceView: insufficient_evidence,
  lockedOutView: locked_out,
  userAbortedView: user_aborted,
  timeOutView: time_out,
  technicalIVissuesView: technical_iv_issues,
  preconditionFailedView: precondition_failed
)(
  implicit val transformer: NINOLogMessageTransformer,
  val frontendAppConfig: FrontendAppConfig,
  val config: Configuration,
  val env: Environment,
  ec: ExecutionContext
) extends CustomBaseController(cpd, mcc, errorHandler, maintenanceSchedule) with HelpToSaveAuth {

  val eligibilityUrl: String = routes.EligibilityCheckController.getCheckEligibility.url

  val defaultIVUrl: String = appConfig.ivUrl(eligibilityUrl)

  def journeyResult(
    redirectUrl: RedirectUrl, //scalastyle:ignore cyclomatic.complexity method.length
    journeyId: Option[String]
  ): Action[AnyContent] =
    authorisedForHts { implicit request => _ =>
      //Will be populated if we arrived here because of an IV success/failure
      val newIVUrl = frontendAppConfig.ivUrl(redirectUrl.get(OnlyRelative).url)
      lazy val storeNewIVURLThenRedirectTo =
        storeInSessionCacheThenRedirect(HTSSession(None, None, None, Some(newIVUrl), None), journeyId) _
      val urlRegex = "[A-Za-z0-9=&-?/]*"
      if (request.uri.matches(urlRegex)) {
        journeyId match {
          case Some(id) =>
            ivConnector.getJourneyStatus(JourneyId(id)).flatMap {
              case Some(Success) =>
                metrics.ivSuccessCounter.inc()
                storeInSessionCacheThenRedirect(
                  HTSSession(None, None, None, None, Some(redirectUrl.get(OnlyRelative).url)),
                  Some(id)
                )(
                  routes.IvController.getIVSuccessful.url
                )

              case Some(Incomplete) =>
                metrics.ivIncompleteCounter.inc()
                //The journey has not been completed yet.
                //This result can only occur when a service asks for the result too early (before receiving the redirect from IV)
                storeNewIVURLThenRedirectTo(routes.IvController.getTechnicalIssue.url)

              case Some(FailedMatching) =>
                metrics.ivFailedMatchingCounter.inc()
                //The user entered details on the Designatory Details page that could not be matched to an appropriate record in CID
                storeNewIVURLThenRedirectTo(frontendAppConfig.ivFailedMatchingUrl)

              case Some(FailedIV) =>
                metrics.ivFailedIVCounter.inc()
                //The user couldn't answer enough questions correctly to pass verification
                storeNewIVURLThenRedirectTo(routes.IvController.getFailedIV.url)

              case Some(InsufficientEvidence) =>
                metrics.ivInsufficientEvidenceCounter.inc()
                //The user was matched, but we do not have enough information about them to be able to produce the necessary set of questions
                // to ask them to meet the required Confidence Level
                SeeOther(routes.IvController.getInsufficientEvidence.url)

              case Some(UserAborted) =>
                metrics.ivUserAbortedCounter.inc()
                //The user specifically chose to end the journey
                storeNewIVURLThenRedirectTo(routes.IvController.getUserAborted.url)

              case Some(LockedOut) =>
                metrics.ivLockedOutCounter.inc()
                //The user failed to answer questions correctly and exceeded the lockout threshold
                SeeOther(routes.IvController.getLockedOut.url)

              case Some(PrecondFailed) =>
                metrics.ivPreconditionFailedCounter.inc()
                // The user's authority does not meet the criteria for starting an IV journey.
                // This result implies the service should not have sent this user to IV,
                // as this condition can get determined by the user's authority. See below for a list of conditions that lead to this result
                SeeOther(routes.IvController.getPreconditionFailed.url)

              case Some(TechnicalIssue) =>
                metrics.ivTechnicalIssueCounter.inc()
                //A technical issue on the platform caused the journey to end.
                // This is usually a transient issue, so that the user should try again later
                logger.warn("TechnicalIssue response from identityVerificationFrontendService")
                storeNewIVURLThenRedirectTo(routes.IvController.getTechnicalIssue.url)

              case Some(Timeout) =>
                metrics.ivTimeoutCounter.inc()
                //The user took to long to proceed the journey and was timed-out
                storeNewIVURLThenRedirectTo(routes.IvController.getTimedOut.url)

              case _ =>
                logger.warn("unexpected response from identityVerificationFrontendService")
                storeNewIVURLThenRedirectTo(routes.IvController.getTechnicalIssue.url)
            }

          case None =>
            // No journeyId signifies subsequent 2FA failure
            logger.warn("response from identityVerificationFrontendService did not contain token or journeyId param")
            storeNewIVURLThenRedirectTo(routes.IvController.getTechnicalIssue.url)
        }
      } else {
        storeNewIVURLThenRedirectTo(routes.IvController.getTechnicalIssue.url)
    }
    }(loginContinueURL = routes.IvController.journeyResult(redirectUrl, journeyId).url)

  def getIVSuccessful: Action[AnyContent] =
    authorisedForHts { implicit r => implicit h =>
      retrieveURLFromSessionCache(_.ivSuccessURL, eligibilityUrl)(u => Ok(ivSuccessView(u)))

    }(routes.IvController.getIVSuccessful.url)

  def getFailedIV: Action[AnyContent] =
    authorisedForHts { implicit r => implicit h =>
      retrieveURLFromSessionCache(_.ivURL, defaultIVUrl)(u => Ok(failedIVview(u)))
    }(routes.IvController.getFailedIV.url)

  def getInsufficientEvidence: Action[AnyContent] =
    authorisedForHts { implicit r => implicit h =>
      Ok(insufficientEvidenceView())
    }(routes.IvController.getInsufficientEvidence.url)

  def getLockedOut: Action[AnyContent] =
    authorisedForHts { implicit r => implicit h =>
      Ok(lockedOutView())
    }(routes.IvController.getLockedOut.url)

  def getUserAborted: Action[AnyContent] =
    authorisedForHts { implicit r => implicit h =>
      retrieveURLFromSessionCache(_.ivURL, defaultIVUrl)(u => Ok(userAbortedView(u)))
    }(routes.IvController.getUserAborted.url)

  def getTimedOut: Action[AnyContent] =
    authorisedForHts { implicit r => implicit h =>
      retrieveURLFromSessionCache(_.ivURL, defaultIVUrl)(u => Ok(timeOutView(u)))
    }(routes.IvController.getTimedOut.url)

  def getTechnicalIssue: Action[AnyContent] =
    authorisedForHts { implicit r => implicit h =>
      retrieveURLFromSessionCache(_.ivURL, defaultIVUrl)(u => Ok(technicalIVissuesView(u)))
    }(routes.IvController.getTechnicalIssue.url)

  def getPreconditionFailed: Action[AnyContent] =
    authorisedForHts { implicit r => implicit h =>
      Ok(preconditionFailedView())
    }(routes.IvController.getPreconditionFailed.url)

  private def storeInSessionCacheThenRedirect(session: HTSSession, journeyId: Option[String])(redirectTo: => String)(
    implicit
    request: Request[_],
    hc: HeaderCarrier
  ): Future[Result] =
    sessionStore
      .store(session)
      .fold(
        { e =>
          logger.warn(
            s"Could not write to session cache after redirect from IV (journey ID: ${journeyId.getOrElse("not found")}): $e"
          )
          internalServerError()
        },
        _ => SeeOther(redirectTo)
      )

  private def retrieveURLFromSessionCache(url: HTSSession => Option[String], defaultUrl: String)(f: String => Result)(
    implicit
    request: Request[_],
    hc: HeaderCarrier
  ): Future[Result] =
    sessionStore.get.fold(
      { e =>
        logger.warn(s"Could not retrieve data from session cache: $e")
        internalServerError()
      }, { mayBeSession =>
        mayBeSession.fold {
          logger.warn(s"no session found for user in mongo, redirecting to $defaultUrl")
          f(defaultUrl)
        } { session =>
          url(session).fold {
            logger.warn("session exists in mongo but required information is not found")
            internalServerError()
          }(
            f
          )
        }
      }
    )

}
