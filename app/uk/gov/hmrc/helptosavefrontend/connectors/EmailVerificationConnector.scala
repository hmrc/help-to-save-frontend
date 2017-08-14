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

import com.google.inject.{ImplementedBy, Inject, Singleton}
import org.joda.time.Period
import play.api.Configuration
import play.api.http.Status._
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.helptosavefrontend.models.VerifyEmailStatus._
import uk.gov.hmrc.helptosavefrontend.models.{EmailVerificationRequest, VerifyEmailStatus}
import uk.gov.hmrc.helptosavefrontend.util.Logging
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.http.ws.WSHttp

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[EmailVerificationConnectorImpl])
trait EmailVerificationConnector {

  def verifyEmail(nino: String, newEmail: String)(implicit hc: HeaderCarrier): Future[VerifyEmailStatus]
  def isVerified(email: String)(implicit hc: HeaderCarrier): Future[Either[VerifyEmailStatus, Boolean]]
}

@Singleton
class EmailVerificationConnectorImpl @Inject() (http: WSHttp, conf: Configuration) extends EmailVerificationConnector with ServicesConfig with Logging {

  val emailVerifyBaseURL = baseUrl("email-verification")
  val verifyEmailURL = s"$emailVerifyBaseURL/email-verification/verification-requests"
  def isVerifiedURL(email: String) = s"$emailVerifyBaseURL/email-verification/verified-email-addresses/$email"

  val continueURL = "email-verification.continue.baseUrl"
  //+ EmailVerificationController.showSuccess.
  val defaultTemplate = "hts_email_verification"

  override def verifyEmail(nino: String, newEmail: String)(implicit hc: HeaderCarrier): Future[VerifyEmailStatus] = {
    conf.getInt("services.email-verification.linkTTLMinutes") match {
      case Some(linkTTLMinutes) ⇒ verify(nino, newEmail, Period.minutes(linkTTLMinutes))
      case None ⇒ throw new RuntimeException("email-verification.link.ttlMinutes not present in configuration")
    }
  }

  private def verify(nino: String, newEmail: String, ttlPeriod: Period)(implicit hc: HeaderCarrier): Future[VerifyEmailStatus] = {
    val postURL = s"""$verifyEmailURL"""
    val json: JsValue = Json.toJson(EmailVerificationRequest(newEmail, nino, "", ttlPeriod.toString, ttlPeriod.toString, Map()))

    http.POST(postURL, json).map { (response: HttpResponse) ⇒
      response.status match {
        case OK | CREATED =>
          logger.info("[EmailVerification] - Successful return of data")
          Verifing(nino, newEmail)
        case BAD_REQUEST ⇒
          logger.warn("[EmailVerification] - Bad Request")
          RequestNotValidError(nino)
        case CONFLICT ⇒
          logger.info("[EmailVerification] - Email already verified")
          AlreadyVerified(nino, newEmail)
        case SERVICE_UNAVAILABLE ⇒
          logger.warn("[EmailVerification] - Email Verification service not available")
          VerificationServiceUnavailable()
        case _ ⇒
          logger.warn("[EmailVerification] - Unexpected status received from email verification")
          BackendError("Unexpected response from email verification service. Status = " + response.status + ", body = " + response.body.toString)
      }
    }
  }

  def isVerified(email: String)(implicit hc: HeaderCarrier): Future[Either[VerifyEmailStatus, Boolean]] = {
    val getURL = isVerifiedURL(email)
    http.GET(getURL).map { response ⇒
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
