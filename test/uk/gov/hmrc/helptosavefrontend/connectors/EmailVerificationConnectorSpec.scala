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
import play.api.Configuration
import play.api.http.Status
import play.api.libs.json.{JsValue, Writes}
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.config.WSHttp
import uk.gov.hmrc.helptosavefrontend.models.VerifyEmailStatus
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpReads, HttpResponse}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class EmailVerificationConnectorSpec extends UnitSpec with TestSupport
{

  val nino = "AE123XXXX"
  val email = "email@gmail.com"
  val mockHttp = mock[WSHttp]
  lazy val connector = {
    val mockConf = mock[Configuration]
    (mockConf.getInt(_: String)).expects("services.email-verification.linkTTLMinutes").returning(Some(120))
    mockConf
    new EmailVerificationConnectorImpl(mockHttp, mockConf)
  }

  def mockPost(returnedStatus: Int, returnedData: Option[JsValue]): Unit =
    (mockHttp.post(_: String, _: JsValue, _: Seq[(String, String)])(_: Writes[Any], _: HeaderCarrier)).expects(*, *, *, *, *)
      .returning(Future.successful(HttpResponse(returnedStatus, returnedData)))
  

  def mockGet(returnedStatus: Int, returnedData: Option[JsValue]): Unit =
    (mockHttp.get(_: String) (_: HeaderCarrier)).expects(*, *)
      .returning(Future.successful(HttpResponse(returnedStatus, returnedData)))
  
  
  "verifyEmail" should {
    "does good json equal 201" in {
      mockPost(Status.OK, None)
      await(connector.verifyEmail(nino, email)) shouldBe VerifyEmailStatus.Verifing(nino, email)
    }

    "does bad json equal 400" in {
      mockPost(Status.BAD_REQUEST, None)
      await(connector.verifyEmail(nino, email)) shouldBe VerifyEmailStatus.RequestNotValidError(nino)
    }

    "has the email already been verified and results in 409" in {
      mockPost(Status.CONFLICT, None)
      await(connector.verifyEmail(nino, email)) shouldBe VerifyEmailStatus.AlreadyVerified(nino, email)
    }

    "do we get a verification service unavailable error when the email verification service is down" in {
      mockPost(Status.SERVICE_UNAVAILABLE, None)
      await(connector.verifyEmail(nino, email)) shouldBe VerifyEmailStatus.VerificationServiceUnavailable()
    }

    "does Period produce ISO 8601 duration syntax when requested" in {
      //30 minutes = PT30M
      Duration.ofMinutes(30).toString shouldBe "PT30M"
    }

    "If email TTL does not exist in the configuration throw a runtime exception" in {
      val mockConf = mock[Configuration]
      (mockConf.getInt(_: String)).expects("services.email-verification.linkTTLMinutes").returning(None)
      //val connector = new EmailVerificationConnectorImpl(mock[WSHttp], mockConf)
      an [Exception] should be thrownBy new EmailVerificationConnectorImpl(mock[WSHttp], mockConf)
    }
  }

  "isVerified" should {
    "if the email is verified return Future true" in {
      mockGet(Status.OK, None)
      await(connector.isVerified(email)) shouldBe Right(true)
    }

    "if the email is not verified return Future false" in {
      mockGet(Status.NOT_FOUND, None)
      await(connector.isVerified(email)) shouldBe Right(false)
    }

    "if the email string is not valid return a Future false" in {
      mockGet(Status.NOT_FOUND, None)
      await(connector.isVerified("email")) shouldBe Right(false)
    }

    "if the email verification service is down return a VerificationServiceUnavailable error" in {
      mockGet(Status.SERVICE_UNAVAILABLE, None)
      await(connector.isVerified(email)) shouldBe Left(VerifyEmailStatus.VerificationServiceUnavailable())
    }
  }

  "verifyEmailURL" should {
    "will return the correct url" in {
      connector.verifyEmailURL shouldBe s"http://localhost:9891/email-verification/verification-requests"
    }
  }

  "isVerifiedURL" should {
    "will return the correct url when given an email address" in {
      val email = "email@gmail.com"
      connector.isVerifiedURL(email) shouldBe s"http://localhost:9891/email-verification/verified-email-addresses/$email"
    }
  }
}
