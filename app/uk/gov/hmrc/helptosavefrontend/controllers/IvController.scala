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

import javax.inject.Inject

import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.helptosavefrontend.FrontendAppConfig.HtsDeclarationUrl
import uk.gov.hmrc.helptosavefrontend.auth.HtsCompositePageVisibilityPredicate
import uk.gov.hmrc.helptosavefrontend.connectors.IvConnector
import uk.gov.hmrc.helptosavefrontend.models.iv.IvSuccessResponse._
import uk.gov.hmrc.helptosavefrontend.models.iv.JourneyId
import uk.gov.hmrc.helptosavefrontend.views.html.iv.failure._
import uk.gov.hmrc.helptosavefrontend.views.html.iv.success
import uk.gov.hmrc.helptosavefrontend.views.html.twofactor.you_need_two_factor

import scala.concurrent.Future

class IvController @Inject()(ivConnector: IvConnector, val messagesApi: MessagesApi)
  extends HelpToSaveController with I18nSupport {

  def showUpliftJourneyOutcome: Action[AnyContent] =
    authorisedHtsUser { implicit authContext ⇒
      implicit request ⇒
        //Will be populated if we arrived here because of an IV success/failure
        val journeyId = request.getQueryString("token").orElse(request.getQueryString("journeyId"))
        val ivRetryUrl = HtsCompositePageVisibilityPredicate.ivUpliftURI.toString
        val twoFactorRetryUrl = HtsCompositePageVisibilityPredicate.twoFactorURI.toString
        val allowContinue = true

        journeyId match {
          case Some(id) ⇒
            ivConnector.getJourneyStatus(JourneyId(id)).map {
              case Some(Success) ⇒
                Ok(success.iv_success(HtsDeclarationUrl))

              case Some(Incomplete) ⇒
                //The journey has not been completed yet.
                //This result can only occur when a service asks for the result too early (before receiving the redirect from IV)
                InternalServerError(user_aborted_or_incomplete(ivRetryUrl, allowContinue))

              case Some(FailedMatching) ⇒
                //The user entered details on the Designatory Details page that could not be matched to an appropriate record in CID
                Unauthorized(failed_matching(ivRetryUrl))

              case Some(FailedIV) ⇒
                //The user couldn't answer enough questions correctly to pass verification
                Unauthorized(failed_matching(ivRetryUrl))

              case Some(InsufficientEvidence) ⇒
                //The user was matched, but we do not have enough information about them to be able to produce the necessary set of questions
                // to ask them to meet the required Confidence Level
                Unauthorized(insufficient_evidence())

              case Some(UserAborted) ⇒
                //The user specifically chose to end the journey
                Unauthorized(user_aborted_or_incomplete(ivRetryUrl, allowContinue))

              case Some(LockedOut) ⇒
                //The user failed to answer questions correctly and exceeded the lockout threshold
                Unauthorized(locked_out())

              case Some(PrecondFailed) ⇒
                // The user's authority does not meet the criteria for starting an IV journey.
                // This result implies the service should not have sent this user to IV,
                // as this condition can get determined by the user's authority. See below for a list of conditions that lead to this result
                Unauthorized(cant_confirm_identity(ivRetryUrl, allowContinue))

              case Some(TechnicalIssue) ⇒
                //A technical issue on the platform caused the journey to end.
                // This is usually a transient issue, so that the user should try again later
                Logger.warn(s"TechnicalIssue response from identityVerificationFrontendService")
                Unauthorized(user_aborted_or_incomplete(ivRetryUrl, allowContinue))

              case Some(Timeout) ⇒
                //The user took to long to proceed the journey and was timed-out
                Unauthorized(cant_confirm_identity(ivRetryUrl, allowContinue))

              case _ =>
                Logger.error(s"unexpected response from identityVerificationFrontendService")
                InternalServerError(technical_iv_issues(ivRetryUrl))
            }

          case None =>
            // No journeyId signifies subsequent 2FA failure
            Logger.warn(s"response from identityVerificationFrontendService did not contain token or journeyId param")
            Future.successful(Unauthorized(you_need_two_factor(twoFactorRetryUrl)))
        }
    }
}
