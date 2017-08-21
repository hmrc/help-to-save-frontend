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

import play.api.Configuration
import play.api.http.Status
import play.api.libs.json.{JsValue, Writes}
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.config.WSHttp
import uk.gov.hmrc.helptosavefrontend.models.VerifyEmailError
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class EmailVerificationConnectorSpec extends UnitSpec with TestSupport with ServicesConfig
{
  lazy val emailVerifyBaseURL = baseUrl("email-verification")
  val nino = "AE123XXXX"
  val email = "email@gmail.com"
  val mockHttp = mock[WSHttp]

  lazy val connector = {
    val config = Configuration("microservice.services.email-verification.linkTTLMinutes" → " 120")
    new EmailVerificationConnectorImpl(mockHttp, config)
  }

  def mockPost(returnedStatus: Int, returnedData: Option[JsValue]): Unit = {
    val verifyEmailURL = s"$emailVerifyBaseURL/email-verification/verification-requests"
    (mockHttp.post(_: String, _: JsValue, _: Seq[(String, String)])(_: Writes[Any], _: HeaderCarrier)).expects(verifyEmailURL, *, *, *, *)
      .returning(Future.successful(HttpResponse(returnedStatus, returnedData)))
  }


  def mockGet(returnedStatus: Int, email: String, returnedData: Option[JsValue]): Unit = {
    val verifyEmailURL = s"$emailVerifyBaseURL/email-verification/verified-email-addresses/$email"
    (mockHttp.get(_: String)(_: HeaderCarrier)).expects(verifyEmailURL, *)
      .returning(Future.successful(HttpResponse(returnedStatus, returnedData)))
  }


  "verifyEmail" should {
    "return 201 when given good json" in {
      mockPost(Status.OK, None)
      await(connector.verifyEmail(nino, email)) shouldBe Right(())
    }

    "return 400 when given bad json" in {
      mockPost(Status.BAD_REQUEST, None)
      await(connector.verifyEmail(nino, email)) shouldBe Left(VerifyEmailError.RequestNotValidError())
    }

    "return 409 when the email has already been verified" in {
      mockPost(Status.CONFLICT, None)
      await(connector.verifyEmail(nino, email)) shouldBe Left(VerifyEmailError.AlreadyVerified())
    }

    "return a verification service unavailable error when the email verification service is down" in {
      mockPost(Status.SERVICE_UNAVAILABLE, None)
      await(connector.verifyEmail(nino, email)) shouldBe Left(VerifyEmailError.VerificationServiceUnavailable())
    }

    "throw a runtime exception If email TTL does not exist in the configuration" in {
      val config = Configuration("x" → "y")
      an [Exception] should be thrownBy new EmailVerificationConnectorImpl(mock[WSHttp], config)
    }
  }

  "isVerified" should {
    "return a Future true if the email is verified" in {
      mockGet(Status.OK, email, None)
      await(connector.isVerified(email)) shouldBe Right(true)
    }

    "return a Future false if the email is not verified" in {
      mockGet(Status.NOT_FOUND, email, None)
      await(connector.isVerified(email)) shouldBe Right(false)
    }

    "return a Future false if the email string is not valid" in {
      mockGet(Status.NOT_FOUND, "email", None)
      await(connector.isVerified("email")) shouldBe Right(false)
    }

    "return a VerificationServiceUnavailable error if the email verification service is down" in {
      mockGet(Status.SERVICE_UNAVAILABLE, email, None)
      await(connector.isVerified(email)) shouldBe Left(VerifyEmailError.VerificationServiceUnavailable())
    }
  }

  "verifyEmailURL" should {
    "return the correct url" in {
      connector.verifyEmailURL shouldBe "http://localhost:9891/email-verification/verification-requests"
    }
  }

  "isVerifiedURL" should {
    "return the correct url when given an email address" in {
      val email = "email@gmail.com"
      connector.isVerifiedURL(email) shouldBe s"http://localhost:9891/email-verification/verified-email-addresses/$email"
    }
  }

  "continueURL" should {
    "return the correct url" in {
      connector.continueURL shouldBe "http://localhost:7000/help-to-save/register/check-and-confirm-your-details"
    }
  }
}