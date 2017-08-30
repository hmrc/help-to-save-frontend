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
import uk.gov.hmrc.helptosavefrontend.models.{EmailVerificationRequest, VerifyEmailError}
import uk.gov.hmrc.helptosavefrontend.models.VerifyEmailError.BackendError
import uk.gov.hmrc.helptosavefrontend.util.Crypto
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class EmailVerificationConnectorSpec extends UnitSpec with TestSupport with ServicesConfig {

  lazy val emailVerifyBaseURL = baseUrl("email-verification")
  val nino = "AE123XXXX"
  val email = "email@gmail.com"
  val mockHttp = mock[WSHttp]
  implicit val crypto = mock[Crypto]
  val emailVerificationRequest =
    EmailVerificationRequest(
      email,
      "awrs_email_verification",
      "PT2H",
      "http://localhost:7000/help-to-save/check-and-confirm-your-details?p=",
      Map("email" → email, "nino" → nino))

  lazy val connector = {
    val config = Configuration("microservice.services.email-verification.linkTTLMinutes" → " 120",
      "microservice.services.email-verification.continue-url" ->
        "http://localhost:7000/help-to-save/check-and-confirm-your-details"
    )
    new EmailVerificationConnectorImpl(mockHttp, config)
  }

  def mockPost[A](expectedBody: A)(returnedStatus: Int, returnedData: Option[JsValue]): Unit = {
    val verifyEmailURL = s"$emailVerifyBaseURL/email-verification/verification-requests"
    (mockHttp.post(_: String, _: A, _: Seq[(String, String)])(_: Writes[Any], _: HeaderCarrier))
      .expects(verifyEmailURL, expectedBody, Seq.empty[(String, String)], *, *)
      .returning(Future.successful(HttpResponse(returnedStatus, returnedData)))
  }

  def mockPostFailure[A](expectedBody: A): Unit = {
    val verifyEmailURL = s"$emailVerifyBaseURL/email-verification/verification-requests"
    (mockHttp.post(_: String, _: A, _: Seq[(String, String)])(_: Writes[Any], _: HeaderCarrier))
      .expects(verifyEmailURL, expectedBody, Seq.empty[(String, String)], *, *)
      .returning(Future.failed(new Exception("Oh no!")))
  }

  def mockGet(returnedStatus: Int, email: String, returnedData: Option[JsValue]): Unit = {
    val verifyEmailURL = s"$emailVerifyBaseURL/email-verification/verified-email-addresses/$email"
    (mockHttp.get(_: String)(_: HeaderCarrier)).expects(verifyEmailURL, *)
      .returning(Future.successful(HttpResponse(returnedStatus, returnedData)))
  }

  def mockGetFailure(): Unit = {
    val verifyEmailURL = s"$emailVerifyBaseURL/email-verification/verified-email-addresses/$email"
    (mockHttp.get(_: String)(_: HeaderCarrier)).expects(verifyEmailURL, *)
      .returning(Future.failed(new Exception("Uh oh!")))
  }

  def mockEncrypt(expected: String)(result: String): Unit =
    (crypto.encrypt(_: String)).expects(expected).returning(result)

  "verifyEmail" should {
    "return 201 when given good json" in {
      mockEncrypt(nino + "§" + email)("")
      mockPost(emailVerificationRequest)(Status.OK, None)
      await(connector.verifyEmail(nino, email)) shouldBe Right(())
    }

    "return 400 when given bad json" in {
      mockEncrypt(nino + "§" + email)("")
      mockPost(emailVerificationRequest)(Status.BAD_REQUEST, None)
      await(connector.verifyEmail(nino, email)) shouldBe Left(VerifyEmailError.RequestNotValidError)
    }

    "return 409 when the email has already been verified" in {
      mockEncrypt(nino + "§" + email)("")
      mockPost(emailVerificationRequest)(Status.CONFLICT, None)
      await(connector.verifyEmail(nino, email)) shouldBe Left(VerifyEmailError.AlreadyVerified)
    }

    "return a verification service unavailable error when the email verification service is down" in {
      mockEncrypt(nino + "§" + email)("")
      mockPost(emailVerificationRequest)(Status.SERVICE_UNAVAILABLE, None)
      await(connector.verifyEmail(nino, email)) shouldBe Left(VerifyEmailError.VerificationServiceUnavailable)
    }

    "throw a runtime exception If email TTL does not exist in the configuration" in {
      val config = Configuration("x" → "y")
      an[Exception] should be thrownBy new EmailVerificationConnectorImpl(mock[WSHttp], config)
    }

    "should return a back end error if the future failed" in {
      mockEncrypt(nino + "§" + email)("")
      mockPostFailure(emailVerificationRequest)
      await(connector.verifyEmail(nino, email)) shouldBe Left(BackendError)
    }
  }

  "verifyEmailURL" should {
    "return the correct url" in {
      connector.verifyEmailURL shouldBe "http://localhost:7002/email-verification/verification-requests"
    }
  }

  "continueURL" should {
    "return the correct url" in {
      connector.continueURL shouldBe "http://localhost:7000/help-to-save/check-and-confirm-your-details"
    }
  }
}
