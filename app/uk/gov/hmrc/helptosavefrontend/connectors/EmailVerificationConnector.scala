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

package uk.gov.hmrc.helptosavefrontend.connectors

import java.time.Duration

import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.Configuration
import play.api.http.Status._
import uk.gov.hmrc.helptosavefrontend.config.WSHttp
import uk.gov.hmrc.helptosavefrontend.models.VerifyEmailError._
import uk.gov.hmrc.helptosavefrontend.models.{EmailVerificationRequest, VerifyEmailError}
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, EmailVerificationParams, Logging}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal

@ImplementedBy(classOf[EmailVerificationConnectorImpl])
trait EmailVerificationConnector {

  def verifyEmail(nino: String, newEmail: String)(implicit hc: HeaderCarrier): Future[Either[VerifyEmailError, Unit]]

}

@Singleton
class EmailVerificationConnectorImpl @Inject() (http: WSHttp)(implicit crypto: Crypto)
  extends EmailVerificationConnector with ServicesConfig with Logging {

  val linkTTLMinutes: Int = getInt("microservice.services.email-verification.linkTTLMinutes")
  val emailVerifyBaseURL: String = baseUrl("email-verification")
  val verifyEmailURL: String = s"$emailVerifyBaseURL/email-verification/verification-requests"

  val continueURL: String = getString("microservice.services.email-verification.continue-url")
  val templateId: String = "awrs_email_verification"

  def verifyEmail(nino: String, newEmail: String)(implicit hc: HeaderCarrier): Future[Either[VerifyEmailError, Unit]] = {
    val continueUrlWithParams = continueURL + "?p=" + EmailVerificationParams(nino, newEmail).encode()

    val verificationRequest = EmailVerificationRequest(
      newEmail,
      nino,
      templateId,
      Duration.ofMinutes(linkTTLMinutes).toString,
      continueUrlWithParams,
      Map.empty[String, String])

    http.post[EmailVerificationRequest](verifyEmailURL, verificationRequest).map[Either[VerifyEmailError, Unit]]{ (response: HttpResponse) ⇒
      response.status match {
        case OK | CREATED ⇒
          logger.info(s"[EmailVerification] - Email verification successfully triggered")
          Right(())

        case BAD_REQUEST ⇒
          logger.warn("[EmailVerification] - Bad Request from email verification service")
          Left(RequestNotValidError)

        case CONFLICT ⇒
          logger.info("[EmailVerification] - Email address already verified")
          Left(AlreadyVerified)

        case SERVICE_UNAVAILABLE ⇒
          logger.warn("[EmailVerification] - Email Verification service not currently available")
          Left(VerificationServiceUnavailable)

        case status ⇒
          logger.warn(s"[EmailVerification] - Unexpected status $status received from email verification body = ${response.body}")
          Left(BackendError)
      }
    }.recover{
      case NonFatal(e) ⇒
        logger.warn(s"Error while calling email verification service: ${e.getMessage}")
        Left(BackendError)
    }
  }

}
