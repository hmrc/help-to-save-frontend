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
import uk.gov.hmrc.helptosavefrontend.util.Logging
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[EmailVerificationConnectorImpl])
trait EmailVerificationConnector {

  def verifyEmail(nino: String, newEmail: String)(implicit hc: HeaderCarrier): Future[Either[VerifyEmailError, Unit]]

  def isVerified(email: String)(implicit hc: HeaderCarrier): Future[Either[VerifyEmailError, Boolean]]

}

@Singleton
class EmailVerificationConnectorImpl @Inject()(http: WSHttp, conf: Configuration) extends EmailVerificationConnector with ServicesConfig with Logging {

  val linkTTLMinutes = conf.underlying.getInt("services.email-verification.linkTTLMinutes")
  val emailVerifyBaseURL = baseUrl("email-verification")
  val verifyEmailURL = s"$emailVerifyBaseURL/email-verification/verification-requests"

  def isVerifiedURL(email: String) = s"$emailVerifyBaseURL/email-verification/verified-email-addresses/$email"

  val continueURL = "email-verification.continue.baseUrl"
  val templateId = "hts_email_verification"

  def verifyEmail(nino: String, newEmail: String)(implicit hc: HeaderCarrier): Future[Either[VerifyEmailError, Unit]] = {
    val verificationRequest = EmailVerificationRequest(newEmail, nino, templateId, Duration.ofMinutes(linkTTLMinutes).toString, continueURL, Map())

    http.post(verifyEmailURL, verificationRequest).map { (response: HttpResponse) ⇒
      response.status match {
        case OK | CREATED =>
          logger.info("[EmailVerification] - Email verification successfully triggered")
          Right(())
        case BAD_REQUEST ⇒
          logger.warn("[EmailVerification] - Bad Request from email verification service")
          Left(RequestNotValidError(nino))
        case CONFLICT ⇒
          logger.info("[EmailVerification] - Email already verified")
          Left(AlreadyVerified(nino, newEmail))
        case SERVICE_UNAVAILABLE ⇒
          logger.warn("[EmailVerification] - Email Verification service not available")
          Left(VerificationServiceUnavailable())
        case _ ⇒
          logger.warn("[EmailVerification] - Unexpected status received from email verification")
          Left(BackendError(s"Unexpected response from email verification service. Status = ${response.status}, body = ${response.body}"))
      }
    }
  }

  def isVerified(email: String)(implicit hc: HeaderCarrier): Future[Either[VerifyEmailError, Boolean]] = {
    val getURL = isVerifiedURL(email)
    http.get(getURL).map { response ⇒
      response.status match {
        case OK ⇒
          logger.info("[EmailVerification] - email is verified")
          Right(true)
        case NOT_FOUND ⇒
          logger.warn("[EmailVerification] - email is not verified")
          Right(false)
        case SERVICE_UNAVAILABLE ⇒
          logger.warn("[EmailVerification] - Email Verification service not available")
          Left(VerificationServiceUnavailable())
        case _ ⇒
          logger.warn("[EmailVerification] - Unexpected status received from email verification")
          Left(BackendError(s"Unexpected response from email verification service. Status = ${response.status}, body = ${response.body.toString}"))
      }
    }
  }
}