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

import play.api.i18n.Messages
import play.api.mvc.{AnyContent, Request, Result}
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.connectors.EmailVerificationConnector
import uk.gov.hmrc.helptosavefrontend.models.VerifyEmailError.{AlreadyVerified, BadContinueURL}
import uk.gov.hmrc.helptosavefrontend.models.{HtsContextWithNINO, SuspiciousActivity}
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, EmailVerificationParams, toFuture}
import uk.gov.hmrc.helptosavefrontend.views
import uk.gov.hmrc.helptosavefrontend.util.Logging._

import scala.concurrent.Future
import scala.util.{Failure, Success}

trait VerifyEmailBehaviour { this: HelpToSaveAuth ⇒

  val emailVerificationConnector: EmailVerificationConnector

  val auditor: HTSAuditor

  def sendEmailVerificationRequest(email:                String,
                                   ifSuccess:            ⇒ Result,
                                   ifAlreadyVerifiedURL: EmailVerificationParams ⇒ String,
                                   isNewApplicant:       Boolean)(implicit request: Request[AnyContent],
                                                                  htsContext: HtsContextWithNINO,
                                                                  crypto:     Crypto,
                                                                  messages:   Messages): Future[Result] =
    emailVerificationConnector.verifyEmail(htsContext.nino, email, isNewApplicant).map {
      case Right(_)              ⇒ ifSuccess
      case Left(AlreadyVerified) ⇒ SeeOther(ifAlreadyVerifiedURL(EmailVerificationParams(htsContext.nino, email)))
      case Left(other)           ⇒ Ok(views.html.email.email_verify_error(other))
    }

  def handleEmailVerified(emailVerificationParams: String,
                          ifValid:                 EmailVerificationParams ⇒ Future[Result])(implicit request: Request[AnyContent],
                                                                                             htsContext: HtsContextWithNINO,
                                                                                             crypto:     Crypto,
                                                                                             messages:   Messages): Future[Result] =
    EmailVerificationParams.decode(emailVerificationParams) match {

      case Failure(e) ⇒
        val nino = htsContext.nino
        logger.warn("SuspiciousActivity: malformed redirect from email verification service back to HtS, " +
          s"could not decode email verification parameters: ${e.getMessage}", e, nino)
        auditor.sendEvent(SuspiciousActivity(Some(nino), "malformed_redirect"), nino)
        Ok(views.html.email.email_verify_error(BadContinueURL))

      case Success(params) ⇒
        ifValid(params)
    }

}
