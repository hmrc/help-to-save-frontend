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

import play.api.libs.json.Json
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig.{eligibilityCheckUrl, encoded}
import uk.gov.hmrc.helptosavefrontend.config.WSHttpExtension
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.util.NINO
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class HelpToSaveConnectorSpec extends TestSupport {

  def eligibilityURL(nino: NINO, authorisationCode: String): String =
    s"$eligibilityCheckUrl?nino=$nino&oauthAuthorisationCode=$authorisationCode"

  class TestApparatus {
    val mockHttp = mock[WSHttpExtension]

    val connector = new HelpToSaveConnectorImpl {
      override val http = mockHttp
    }

    def mockGetEligibilityStatus[I](url: String)(result: HttpResponse): Unit =
      (mockHttp.get(_: String)(_: HeaderCarrier))
        .expects(url, *)
        .returning(Future.successful(result))
  }

  "The HelpToSaveConnectorImpl" when {

    "getting eligibility status" should {

      val nino = "nino"
      val authorisationCode = "authorisation-code"

      "perform a GET request to the help-to-save-service" in new TestApparatus {
        mockGetEligibilityStatus(eligibilityURL(nino, authorisationCode))(HttpResponse(200))
        connector.getEligibility(nino, authorisationCode)
      }

      "return an EligibilityResult if the call comes back with a 200 status with a positive result" in new TestApparatus {
        val userInfo = randomUserInfo()
        mockGetEligibilityStatus(eligibilityURL(nino, authorisationCode))(
          HttpResponse(200, responseJson = Some(Json.toJson(EligibilityCheckResult(Right(Some(userInfo)))))))

        val result = connector.getEligibility(nino, authorisationCode)
        Await.result(result.value, 3.seconds) shouldBe Right(EligibilityCheckResult(Right(Some(userInfo))))
      }

      "return an EligibilityResult if the call comes back with a 200 status with a negative result" in new TestApparatus {
        mockGetEligibilityStatus(eligibilityURL(nino, authorisationCode))(
          HttpResponse(200, responseJson = Some(Json.toJson(EligibilityCheckResult(Right(None))))))

        val result = connector.getEligibility(nino, authorisationCode)
        Await.result(result.value, 3.seconds) shouldBe Right(EligibilityCheckResult(Right(None)))
      }


      "return an error if the call does not come back with a 200 status" in new TestApparatus {
        mockGetEligibilityStatus(eligibilityURL(nino, authorisationCode))(HttpResponse(500))

        val result = connector.getEligibility(nino, authorisationCode)
        Await.result(result.value, 3.seconds).isLeft shouldBe true
      }

    }
  }
}
