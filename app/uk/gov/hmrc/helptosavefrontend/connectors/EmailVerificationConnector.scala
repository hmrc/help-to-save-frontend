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
import play.api.http.Status._
import uk.gov.hmrc.helptosavefrontend.config.WSHttp
import uk.gov.hmrc.helptosavefrontend.controllers.email.UserType
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics.nanosToPrettyString
import uk.gov.hmrc.helptosavefrontend.models.VerifyEmailError._
import uk.gov.hmrc.helptosavefrontend.models.{EmailVerificationRequest, VerifyEmailError}
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, EmailVerificationParams, Logging, NINO}
import uk.gov.hmrc.helptosavefrontend.util.Logging._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@ImplementedBy(classOf[EmailVerificationConnectorImpl])
trait EmailVerificationConnector {

  def verifyEmail(nino: String, newEmail: String)(implicit hc: HeaderCarrier, ec: ExecutionContext, userType: UserType): Future[Either[VerifyEmailError, Unit]]

}

@Singleton
class EmailVerificationConnectorImpl @Inject() (http: WSHttp, metrics: Metrics)(implicit crypto: Crypto)
  extends EmailVerificationConnector with ServicesConfig with Logging {

  val linkTTLMinutes: Int = getInt("microservice.services.email-verification.linkTTLMinutes")
  val emailVerifyBaseURL: String = baseUrl("email-verification")
  val verifyEmailURL: String = s"$emailVerifyBaseURL/email-verification/verification-requests"

  val newApplicantContinueURL: String = getString("microservice.services.email-verification.continue-url.new-applicant")
  val accountHolderContinueURL: String = getString("microservice.services.email-verification.continue-url.account-holder")

  val templateId: String = "awrs_email_verification"

  def verifyEmail(nino: String, newEmail: String)(implicit hc: HeaderCarrier, ec: ExecutionContext, userType: UserType): Future[Either[VerifyEmailError, Unit]] = {
    val continueUrlWithParams = {
      val continueURL = userType.fold(newApplicantContinueURL, accountHolderContinueURL)
      continueURL + "?p=" + EmailVerificationParams(nino, newEmail).encode()
    }

    val verificationRequest = EmailVerificationRequest(
      newEmail,
      nino,
      templateId,
      Duration.ofMinutes(linkTTLMinutes).toString,
      continueUrlWithParams,
      Map.empty[String, String])

    val timerContext = metrics.emailVerificationTimer.time()

    http.post[EmailVerificationRequest](verifyEmailURL, verificationRequest).map[Either[VerifyEmailError, Unit]]{ (response: HttpResponse) ⇒
      val time = timerContext.stop()

      response.status match {
        case OK | CREATED ⇒
          logger.info(s"[EmailVerification] - Email verification successfully triggered (time: ${nanosToPrettyString(time)})", nino)
          Right(())

        case other ⇒
          handleErrorStatus(other, response, time, nino)
      }
    }.recover{
      case NonFatal(e) ⇒
        val time = timerContext.stop()
        metrics.emailVerificationErrorCounter.inc()

        logger.warn(s"Error while calling email verification service: ${e.getMessage} (time: ${nanosToPrettyString(time)})", nino)
        Left(BackendError)
    }
  }

  private def handleErrorStatus(status: Int, response: HttpResponse, time: Long, nino: NINO): Either[VerifyEmailError, Unit] = {
    metrics.emailVerificationErrorCounter.inc()

    status match {
      case BAD_REQUEST ⇒
        logger.warn(s"[EmailVerification] - Bad Request from email verification service (time: ${nanosToPrettyString(time)})", nino)
        Left(RequestNotValidError)

      case CONFLICT ⇒
        logger.info(s"[EmailVerification] - Email address already verified (time: ${nanosToPrettyString(time)})", nino)
        Left(AlreadyVerified)

      case SERVICE_UNAVAILABLE ⇒
        logger.warn(s"[EmailVerification] - Email Verification service not currently available (time: ${nanosToPrettyString(time)})", nino)
        Left(VerificationServiceUnavailable)

      case other ⇒
        logger.warn(s"[EmailVerification] - Unexpected status $other received from email verification" +
          s" body = ${response.body} (time: ${nanosToPrettyString(time)})", nino)
        Left(BackendError)
    }
  }

}
