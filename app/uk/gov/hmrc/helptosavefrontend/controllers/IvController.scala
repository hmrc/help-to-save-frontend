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
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig._
import uk.gov.hmrc.helptosavefrontend.config.FrontendAuthConnector
import uk.gov.hmrc.helptosavefrontend.connectors.{IvConnector, SessionCacheConnector}
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.models.iv.IvSuccessResponse._
import uk.gov.hmrc.helptosavefrontend.models.iv.JourneyId
import uk.gov.hmrc.helptosavefrontend.util.Logging._
import uk.gov.hmrc.helptosavefrontend.util.{Logging, urlDecode}
import uk.gov.hmrc.helptosavefrontend.views.html.access_denied
import uk.gov.hmrc.helptosavefrontend.views.html.iv._

import scala.concurrent.Future

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
    val journeyId = request.getQueryString("token").orElse(request.getQueryString("journeyId"))
    val allowContinue = true
    val newIvUrl = ivUrl(continueURL)
    val nino = htsContext.nino

    journeyId match {
      case Some(id) ⇒
        ivConnector.getJourneyStatus(JourneyId(id)).map {
          case Some(Success) ⇒
            metrics.ivSuccessCounter.inc()
            Ok(iv_success(urlDecode(continueURL)))

          case Some(Incomplete) ⇒
            metrics.ivIncompleteCounter.inc()
            //The journey has not been completed yet.
            //This result can only occur when a service asks for the result too early (before receiving the redirect from IV)
            internalServerError()

          case Some(FailedMatching) ⇒
            metrics.ivFailedMatchingCounter.inc()
            //The user entered details on the Designatory Details page that could not be matched to an appropriate record in CID
            Unauthorized(failed_matching(newIvUrl))

          case Some(FailedIV) ⇒
            metrics.ivFailedIVCounter.inc()
            //The user couldn't answer enough questions correctly to pass verification
            Unauthorized(failed_matching(newIvUrl))

          case Some(InsufficientEvidence) ⇒
            metrics.ivInsufficientEvidenceCounter.inc()
            //The user was matched, but we do not have enough information about them to be able to produce the necessary set of questions
            // to ask them to meet the required Confidence Level
            Unauthorized(insufficient_evidence())

          case Some(UserAborted) ⇒
            metrics.ivUserAbortedCounter.inc()
            //The user specifically chose to end the journey
            Unauthorized(user_aborted_or_incomplete(newIvUrl, allowContinue))

          case Some(LockedOut) ⇒
            metrics.ivLockedOutCounter.inc()
            //The user failed to answer questions correctly and exceeded the lockout threshold
            Unauthorized(locked_out())

          case Some(PrecondFailed) ⇒
            metrics.ivPreconditionFailedCounter.inc()
            // The user's authority does not meet the criteria for starting an IV journey.
            // This result implies the service should not have sent this user to IV,
            // as this condition can get determined by the user's authority. See below for a list of conditions that lead to this result
            Unauthorized(precondition_failed())

          case Some(TechnicalIssue) ⇒
            metrics.ivTechnicalIssueCounter.inc()
            //A technical issue on the platform caused the journey to end.
            // This is usually a transient issue, so that the user should try again later
            logger.warn("TechnicalIssue response from identityVerificationFrontendService", nino)
            Unauthorized(technical_iv_issues(newIvUrl))

          case Some(Timeout) ⇒
            metrics.ivTimeoutCounter.inc()
            //The user took to long to proceed the journey and was timed-out
            Unauthorized(time_out(newIvUrl))

          case _ ⇒
            logger.warn("unexpected response from identityVerificationFrontendService", nino)
            internalServerError()
        }

      case None ⇒
        // No journeyId signifies subsequent 2FA failure
        logger.warn("response from identityVerificationFrontendService did not contain token or journeyId param")
        Future.successful(Unauthorized(access_denied()))
    }
  }(redirectOnLoginURL = routes.IvController.journeyResult(continueURL).url)
}
