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
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig.{accountHolderContinueURL, linkTTLMinutes, newApplicantContinueURL, verifyEmailURL}
import uk.gov.hmrc.helptosavefrontend.config.WSHttp
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics.nanosToPrettyString
import uk.gov.hmrc.helptosavefrontend.models.email.{EmailVerificationRequest, VerifyEmailError}
import uk.gov.hmrc.helptosavefrontend.models.email.VerifyEmailError._
import uk.gov.hmrc.helptosavefrontend.util.Logging._
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, EmailVerificationParams, Logging, NINO, NINOLogMessageTransformer}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@ImplementedBy(classOf[EmailVerificationConnectorImpl])
trait EmailVerificationConnector {

  def verifyEmail(nino:           String,
                  newEmail:       String,
                  firstName:      String,
                  isNewApplicant: Boolean)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[VerifyEmailError, Unit]]

}

@Singleton
class EmailVerificationConnectorImpl @Inject() (http: WSHttp, metrics: Metrics)(implicit crypto: Crypto, transformer: NINOLogMessageTransformer)
  extends EmailVerificationConnector with Logging {

  val templateId: String = "hts_verification_email"

  def verifyEmail(nino:           String,
                  newEmail:       String,
                  firstName:      String,
                  isNewApplicant: Boolean)(
      implicit
      hc: HeaderCarrier, ec: ExecutionContext): Future[Either[VerifyEmailError, Unit]] = {
    val continueUrlWithParams = {
      val continueURL = if (isNewApplicant) newApplicantContinueURL else accountHolderContinueURL
      continueURL + "?p=" + EmailVerificationParams(nino, newEmail).encode()
    }

    val verificationRequest = EmailVerificationRequest(
      newEmail,
      templateId,
      Duration.ofMinutes(linkTTLMinutes).toString,
      continueUrlWithParams,
      Map("name" → firstName))

    val timerContext = metrics.emailVerificationTimer.time()

    http.post[EmailVerificationRequest](verifyEmailURL, verificationRequest).map[Either[VerifyEmailError, Unit]] { (response: HttpResponse) ⇒
      val time = timerContext.stop()

      response.status match {
        case OK | CREATED ⇒
          logger.info(s"Email verification successfully triggered, received status ${response.status} " +
            s"(round-trip time: ${nanosToPrettyString(time)})", nino)
          Right(())

        case other ⇒
          handleErrorStatus(other, response, time, nino)
      }
    }.recover {
      case NonFatal(e) ⇒
        val time = timerContext.stop()
        metrics.emailVerificationErrorCounter.inc()

        logger.warn(s"Error while calling email verification service: ${e.getMessage} (round-trip time: ${nanosToPrettyString(time)})", nino)
        Left(OtherError)
    }
  }

  private def handleErrorStatus(status: Int, response: HttpResponse, time: Long, nino: NINO): Either[VerifyEmailError, Unit] = {
    metrics.emailVerificationErrorCounter.inc()

    status match {
      case BAD_REQUEST ⇒
        logger.warn(s"Received status 400 (Bad Request) (round-trip time: ${nanosToPrettyString(time)})", nino)
        Left(OtherError)

      case CONFLICT ⇒
        logger.info("Email address already verified, received status 409 (Conflict) " +
          s"(round-trip time: ${nanosToPrettyString(time)})", nino)
        Left(AlreadyVerified)

      case SERVICE_UNAVAILABLE ⇒
        logger.warn(s"Received status 503 (Service Unavailable) (round-trip time: ${nanosToPrettyString(time)})", nino)
        Left(OtherError)

      case other ⇒
        logger.warn(s"Received unexpected status $other from email verification" +
          s" body = ${response.body} (round-trip time: ${nanosToPrettyString(time)})", nino)
        Left(OtherError)
    }
  }

}
