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

import cats.data.EitherT
import play.api.mvc.{AnyContent, Request, Result}
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.connectors.EmailVerificationConnector
import uk.gov.hmrc.helptosavefrontend.models.email.VerifyEmailError
import uk.gov.hmrc.helptosavefrontend.models.email.VerifyEmailError.AlreadyVerified
import uk.gov.hmrc.helptosavefrontend.models.{HtsContextWithNINO, HtsContextWithNINOAndUserDetails, SuspiciousActivity}
import uk.gov.hmrc.helptosavefrontend.util.Logging._
import uk.gov.hmrc.helptosavefrontend.util._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

trait VerifyEmailBehaviour extends Logging {
  this: BaseController =>

  val emailVerificationConnector: EmailVerificationConnector

  val auditor: HTSAuditor

  def sendEmailVerificationRequest(
    email: String,
    firstName: String,
    ifSuccess: => Result,
    ifAlreadyVerifiedURL: EmailVerificationParams => String,
    ifFailure: VerifyEmailError => Result,
    isNewApplicant: Boolean
  )(
    implicit request: Request[AnyContent],
    htsContext: HtsContextWithNINO,
    ec: ExecutionContext
  ): Future[Result] =
    emailVerificationConnector.verifyEmail(htsContext.nino, email, firstName, isNewApplicant).map {
      case Right(_)              => ifSuccess
      case Left(AlreadyVerified) => SeeOther(ifAlreadyVerifiedURL(EmailVerificationParams(htsContext.nino, email)))
      case Left(e)               => ifFailure(e)
    }

  def withEmailVerificationParameters(
    emailVerificationParams: String,
    ifValid: EmailVerificationParams => EitherT[Future, String, Result],
    ifInvalid: => EitherT[Future, String, Result]
  )(path: String)(
    implicit request: Request[AnyContent],
    htsContext: HtsContextWithNINOAndUserDetails,
    crypto: Crypto,
    appConfig: FrontendAppConfig,
    transformer: NINOLogMessageTransformer,
    ec: ExecutionContext
  ): EitherT[Future, String, Result] =
    EmailVerificationParams.decode(emailVerificationParams) match {

      case Failure(e) =>
        val nino = htsContext.nino
        logger.warn(
          "SuspiciousActivity: malformed redirect from email verification service back to HtS, " +
            s"could not decode email verification parameters: ${e.getMessage}",
          e,
          nino
        )
        auditor.sendEvent(SuspiciousActivity(Some(nino), "malformed_redirect", path), nino)
        ifInvalid

      case Success(params) =>
        ifValid(params)
    }

}
