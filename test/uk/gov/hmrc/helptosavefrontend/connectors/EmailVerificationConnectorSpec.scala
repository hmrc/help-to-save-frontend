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

import org.joda.time.Period
import play.api.Configuration
import play.api.http.Status
import play.api.libs.json.{JsValue, Writes}
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.models.VerifyEmailStatus
import uk.gov.hmrc.play.http.ws._
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpReads, HttpResponse}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future


class EmailVerificationConnectorSpec extends UnitSpec with TestSupport
{

  val nino = "AE123XXXX"
  val email = "email@gmail.com"

  def getHttpMock(returnedStatus: Int, returnedData: Option[JsValue]): WSHttp = {
    val mockHttp = mock[WSHttp]
    (mockHttp.POST(_: String, _: JsValue, _: Seq[(String, String)])(_: Writes[Any], _: HttpReads[Any], _: HeaderCarrier)).expects(*, *, *, *, *, *)
      .returning(Future.successful(HttpResponse(returnedStatus, returnedData)))
    mockHttp
  }

  def getConfigurationMock(minutes: Int) = {
    val mockConf = mock[Configuration]
    (mockConf.getInt(_: String)).expects("email-verification.link.ttlMinutes").returning(Some(minutes))
    mockConf
  }


  "verifyEmail" should {
    "does good json equal 201" in {
      val http = getHttpMock(Status.OK, None)
      val conf = getConfigurationMock(120)
      val connector = new EmailVerificationConnectorImpl(http, conf)
      await(connector.verifyEmail(nino, email)) shouldBe VerifyEmailStatus.Verifing(nino, email)
    }

    "does bad json equal 400" in {
      val http = getHttpMock(Status.BAD_REQUEST, None)
      val conf = getConfigurationMock(120)
      val connector = new EmailVerificationConnectorImpl(http, conf)
      await(connector.verifyEmail(nino, email)) shouldBe VerifyEmailStatus.RequestNotValidError(nino)
    }

    "has the email already been verified and results in 409" in {
      val http = getHttpMock(Status.CONFLICT, None)
      val conf = getConfigurationMock(120)
      val connector = new EmailVerificationConnectorImpl(http, conf)
      await(connector.verifyEmail(nino, email)) shouldBe VerifyEmailStatus.AlreadyVerified(nino, email)
    }

    "do we get a verification service unavailable error when the email verification service is down" in {
      val http = getHttpMock(Status.NOT_FOUND, None)
      val conf = getConfigurationMock(120)
      val connector = new EmailVerificationConnectorImpl(http, conf)
      await(connector.verifyEmail(nino, email)) shouldBe VerifyEmailStatus.VerificationServiceUnavailable()
    }

    "does Period produce ISO 8601 duration syntax when requested" in {
      //30 minutes = PT30M
      val thirtyMins = Period.minutes(30).toString  shouldBe "PT30M"

    }

    "If email TTL does not exist in the configuration throw a runtime exception" in {
      val mockConf = mock[Configuration]
      (mockConf.getInt(_: String)).expects("email-verification.link.ttlMinutes").returning(None)
      val connector = new EmailVerificationConnectorImpl(mock[WSHttp], mockConf)
      an [Exception] should be thrownBy connector.verifyEmail("AE123456D", "email@gmail.com")

    }


  }


}
