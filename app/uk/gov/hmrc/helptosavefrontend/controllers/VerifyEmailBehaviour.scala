/*
 * Copyright 2018 HM Revenue & Customs
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
import play.api.i18n.Messages
import play.api.mvc.{AnyContent, Request, Result}
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.connectors.EmailVerificationConnector
import uk.gov.hmrc.helptosavefrontend.models.email.VerifyEmailError
import uk.gov.hmrc.helptosavefrontend.models.email.VerifyEmailError.AlreadyVerified
import uk.gov.hmrc.helptosavefrontend.models.{HTSSession, HtsContextWithNINO, HtsContextWithNINOAndUserDetails, SuspiciousActivity}
import uk.gov.hmrc.helptosavefrontend.util._
import uk.gov.hmrc.helptosavefrontend.util.Logging._

import scala.concurrent.Future
import scala.util.{Failure, Success}

trait VerifyEmailBehaviour {
  this: BaseController ⇒

  val emailVerificationConnector: EmailVerificationConnector

  val auditor: HTSAuditor

  def sendEmailVerificationRequest(email:                String,
                                   firstName:            String,
                                   ifSuccess:            ⇒ Result,
                                   ifAlreadyVerifiedURL: EmailVerificationParams ⇒ String,
                                   ifFailure:            VerifyEmailError ⇒ Result,
                                   isNewApplicant:       Boolean)(implicit request: Request[AnyContent],
                                                                  htsContext: HtsContextWithNINO,
                                                                  crypto:     Crypto,
                                                                  messages:   Messages): Future[Result] =
    emailVerificationConnector.verifyEmail(htsContext.nino, email, firstName, isNewApplicant).map {
      case Right(_)              ⇒ ifSuccess
      case Left(AlreadyVerified) ⇒ SeeOther(ifAlreadyVerifiedURL(EmailVerificationParams(htsContext.nino, email)))
      case Left(e)               ⇒ ifFailure(e)
    }

  def withEmailVerificationParameters(emailVerificationParams: String,
                                      ifValid:                 EmailVerificationParams ⇒ EitherT[Future, String, Result],
                                      ifInvalid:               ⇒ EitherT[Future, String, Result])(implicit request: Request[AnyContent],
                                                                                                  htsContext:  HtsContextWithNINOAndUserDetails,
                                                                                                  crypto:      Crypto,
                                                                                                  messages:    Messages,
                                                                                                  transformer: NINOLogMessageTransformer): EitherT[Future, String, Result] =
    EmailVerificationParams.decode(emailVerificationParams) match {

      case Failure(e) ⇒
        val nino = htsContext.nino
        logger.warn("SuspiciousActivity: malformed redirect from email verification service back to HtS, " +
          s"could not decode email verification parameters: ${e.getMessage}", e, nino)
        auditor.sendEvent(SuspiciousActivity(Some(nino), "malformed_redirect"), nino)
        ifInvalid

      case Success(params) ⇒
        ifValid(params)
    }

  def getEmailFromSession(session: Option[HTSSession])(getEmail: HTSSession ⇒ Option[Email], description: String): Either[String, Email] =
    session.fold[Either[String, Email]](
      Left("Could not find session")
    )(getEmail(_).fold[Either[String, Email]](Left(s"Could not find $description in session"))(Right(_)))

}
