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

import javax.inject.{Inject, Singleton}

import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig._
import uk.gov.hmrc.helptosavefrontend.config.FrontendAuthConnector
import uk.gov.hmrc.helptosavefrontend.connectors.{IvConnector, SessionCacheConnector}
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.models.iv.IvSuccessResponse._
import uk.gov.hmrc.helptosavefrontend.models.iv.JourneyId
import uk.gov.hmrc.helptosavefrontend.util.toFuture
import uk.gov.hmrc.helptosavefrontend.util.Logging._
import uk.gov.hmrc.helptosavefrontend.util.{Logging, urlDecode}
import uk.gov.hmrc.helptosavefrontend.views.html.iv._

@Singleton
class IvController @Inject() (val sessionCacheConnector: SessionCacheConnector,
                              ivConnector:               IvConnector,
                              val messagesApi:           MessagesApi,
                              frontendAuthConnector:     FrontendAuthConnector,
                              metrics:                   Metrics)
  extends HelpToSaveAuth(frontendAuthConnector, metrics) with I18nSupport with Logging {

  def journeyResult(continueURL: String): Action[AnyContent] = authorisedForHtsWithNINOAndNoCL { //scalastyle:ignore cyclomatic.complexity method.length
  implicit request ⇒ implicit htsContext ⇒
    //Will be populated if we arrived here because of an IV success/failure
    val journeyId = request.getQueryString("journeyId")
    val newIVUrl = ivUrl(continueURL)
    val nino = htsContext.nino

    journeyId match {
      case Some(id) ⇒
        ivConnector.getJourneyStatus(JourneyId(id)).map {
          case Some(Success) ⇒
            metrics.ivSuccessCounter.inc()
            SeeOther(routes.IvController.getIVSuccessful(continueURL).url)

          case Some(Incomplete) ⇒
            metrics.ivIncompleteCounter.inc()
            //The journey has not been completed yet.
            //This result can only occur when a service asks for the result too early (before receiving the redirect from IV)
            internalServerError()

          case Some(FailedMatching) ⇒
            metrics.ivFailedMatchingCounter.inc()
            //The user entered details on the Designatory Details page that could not be matched to an appropriate record in CID
            SeeOther(routes.IvController.getFailedMatching(newIVUrl).url)

          case Some(FailedIV) ⇒
            metrics.ivFailedIVCounter.inc()
            //The user couldn't answer enough questions correctly to pass verification
            SeeOther(routes.IvController.getFailedIV(newIVUrl).url)

          case Some(InsufficientEvidence) ⇒
            metrics.ivInsufficientEvidenceCounter.inc()
            //The user was matched, but we do not have enough information about them to be able to produce the necessary set of questions
            // to ask them to meet the required Confidence Level
            SeeOther(routes.IvController.getInsufficientEvidence().url)

          case Some(UserAborted) ⇒
            metrics.ivUserAbortedCounter.inc()
            //The user specifically chose to end the journey
            SeeOther(routes.IvController.getUserAborted(newIVUrl).url)

          case Some(LockedOut) ⇒
            metrics.ivLockedOutCounter.inc()
            //The user failed to answer questions correctly and exceeded the lockout threshold
            SeeOther(routes.IvController.getLockedOut().url)

          case Some(PrecondFailed) ⇒
            metrics.ivPreconditionFailedCounter.inc()
            // The user's authority does not meet the criteria for starting an IV journey.
            // This result implies the service should not have sent this user to IV,
            // as this condition can get determined by the user's authority. See below for a list of conditions that lead to this result
            SeeOther(routes.IvController.getPreconditionFailed().url)

          case Some(TechnicalIssue) ⇒
            metrics.ivTechnicalIssueCounter.inc()
            //A technical issue on the platform caused the journey to end.
            // This is usually a transient issue, so that the user should try again later
            logger.warn("TechnicalIssue response from identityVerificationFrontendService", nino)
            SeeOther(routes.IvController.getTechnicalIssue(newIVUrl).url)

          case Some(Timeout) ⇒
            metrics.ivTimeoutCounter.inc()
            //The user took to long to proceed the journey and was timed-out
            SeeOther(routes.IvController.getTimedOut(newIVUrl).url)

          case _ ⇒
            logger.warn("unexpected response from identityVerificationFrontendService", nino)
            internalServerError()
        }

      case None ⇒
        // No journeyId signifies subsequent 2FA failure
        logger.warn("response from identityVerificationFrontendService did not contain token or journeyId param")
        SeeOther(routes.IvController.getTechnicalIssue(newIVUrl).url)
    }
  }(redirectOnLoginURL = routes.IvController.journeyResult(continueURL).url)

  def getIVSuccessful(continueURL: String): Action[AnyContent] =
    authorisedForHtsWithNINOAndNoCL{ implicit r ⇒ implicit h ⇒
      Ok(iv_success(urlDecode(continueURL)))
    }(routes.AccessAccountController.accessAccount().url)

  def getFailedMatching(ivURL: String): Action[AnyContent] =
    authorisedForHtsWithNINOAndNoCL{ implicit r ⇒ implicit h ⇒
      Unauthorized(failed_matching(urlDecode(ivURL)))
    }(routes.AccessAccountController.accessAccount().url)

  def getFailedIV(ivURL: String): Action[AnyContent] =
    authorisedForHtsWithNINOAndNoCL{ implicit r ⇒ implicit h ⇒
      Unauthorized(failed_iv(urlDecode(ivURL)))
    }(routes.AccessAccountController.accessAccount().url)

  def getInsufficientEvidence: Action[AnyContent] =
    authorisedForHtsWithNINOAndNoCL{ implicit r ⇒ implicit h ⇒
      Unauthorized(insufficient_evidence())
    }(routes.AccessAccountController.accessAccount().url)

  def getLockedOut: Action[AnyContent] =
    authorisedForHtsWithNINOAndNoCL{ implicit r ⇒ implicit h ⇒
      Unauthorized(locked_out())
    }(routes.AccessAccountController.accessAccount().url)

  def getUserAborted(ivURL: String): Action[AnyContent] =
    authorisedForHtsWithNINOAndNoCL{ implicit r ⇒ implicit h ⇒
      Unauthorized(user_aborted_or_incomplete(urlDecode(ivURL), allowContinue = true))
    }(routes.AccessAccountController.accessAccount().url)

  def getTimedOut(ivURL: String): Action[AnyContent] =
    authorisedForHtsWithNINOAndNoCL{ implicit r ⇒ implicit h ⇒
      Unauthorized(time_out(urlDecode(ivURL)))
    }(routes.AccessAccountController.accessAccount().url)

  def getTechnicalIssue(ivURL: String): Action[AnyContent] =
    authorisedForHtsWithNINOAndNoCL{ implicit r ⇒ implicit h ⇒
      Unauthorized(technical_iv_issues(urlDecode(ivURL)))
    }(routes.AccessAccountController.accessAccount().url)

  def getPreconditionFailed: Action[AnyContent] =
    authorisedForHtsWithNINOAndNoCL{ implicit r ⇒ implicit h ⇒
      Unauthorized(precondition_failed())
    }(routes.AccessAccountController.accessAccount().url)

}
