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

import java.lang.RuntimeException

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject, Singleton}
import org.joda.time.Period
import play.api.Configuration
import play.api.http.Status._
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.helptosavefrontend.config.{WSHttp, WSHttpExtension}
import uk.gov.hmrc.helptosavefrontend.models.{EmailVerificationRequest, VerifyEmailStatus}
import uk.gov.hmrc.helptosavefrontend.util.Logging
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.HttpResponse
import uk.gov.hmrc.play.http.{HeaderCarrier, _}
import uk.gov.hmrc.helptosavefrontend.controllers.EmailVerificationController
import uk.gov.hmrc.helptosavefrontend.models.VerifyEmailStatus._
import uk.gov.hmrc.play.http.ws.WSHttp

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[EmailVerificationConnectorImpl])
trait EmailVerificationConnector {

  def verifyEmail(nino: String, newEmail: String)(implicit hc: HeaderCarrier): Future[VerifyEmailStatus]

}

@Singleton
class EmailVerificationConnectorImpl @Inject() (http: WSHttp, conf: Configuration) extends EmailVerificationConnector with ServicesConfig with Logging {

  val verifyEmailURL = "http://localhost:9891/email-verification/verification-requests"
  val continueURL = "email-verification.continue.baseUrl" //+ EmailVerificationController.showSuccess.
  val defaultTemplate = "hts_email_verification"

  override def verifyEmail(nino: String, newEmail: String)(implicit hc: HeaderCarrier): Future[VerifyEmailStatus] = {
    conf.getInt("email-verification.link.ttlMinutes") match {
      case Some(linkTTLMinutes) ⇒ verify(nino, newEmail, Period.minutes(linkTTLMinutes))
      case None ⇒ throw new RuntimeException("email-verification.link.ttlMinutes not present in configuration")
    }
  }

  private def verify(nino: String, newEmail: String, ttlPeriod: Period)(implicit hc: HeaderCarrier): Future[VerifyEmailStatus] = {
    val postURL = s"""$verifyEmailURL"""
    val json: JsValue = Json.toJson (EmailVerificationRequest(newEmail, nino, "", ttlPeriod.toString, ttlPeriod.toString, Map()))

    http.POST (postURL, json).map { (response: HttpResponse) ⇒
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
        case NOT_FOUND ⇒
          logger.warn("[EmailVerification] - Email Verification service not available")
          VerificationServiceUnavailable()
        case _ ⇒
          logger.warn("[EmailVerification] - Unexpected status received from back end")
          BackendError("Unexpected response from email verification service. Status = " + response.status + ", body = " + response.body.toString)
      }
    }
  }
}
