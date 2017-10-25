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

import java.util.UUID

import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.Json
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig.ivJourneyResultUrl
import uk.gov.hmrc.helptosavefrontend.config.{FrontendAppConfig, WSHttp}
import uk.gov.hmrc.helptosavefrontend.models.iv.IvSuccessResponse.Success
import uk.gov.hmrc.helptosavefrontend.models.iv.{IvErrorResponse, IvUnexpectedResponse, JourneyId}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

// scalastyle:off magic.number
class IvConnectorSpec extends TestSupport with ScalaFutures {

  class TestApparatus {
    val mockHttp: WSHttp = mock[WSHttp]

    val journeyId = JourneyId(UUID.randomUUID().toString)

    val url = s"$ivJourneyResultUrl/${journeyId.Id}"

    val ivConnector = new IvConnectorImpl(mockHttp)

    def mockHttpResponse(httpResponse: HttpResponse) = {
      (mockHttp.get(_: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(url, *, *)
        .returning(httpResponse)
    }
  }

  "The IvConnectorImpl" when {

    "getting Journey Status" should {

      "handle successful response" in new TestApparatus {

        val httpResponse = HttpResponse(200, Some(Json.parse("""{"result": "Success"}""")))

        mockHttpResponse(httpResponse)

        val result = ivConnector.getJourneyStatus(journeyId)

        result.futureValue should be(Some(Success))
      }

      "handle unexpected non-successful response" in new TestApparatus {

        val httpResponse = HttpResponse(600)

        mockHttpResponse(httpResponse)

        val result = ivConnector.getJourneyStatus(journeyId)

        result.futureValue should be(Some(IvUnexpectedResponse(httpResponse)))
      }

      "handle failure scenarios" in new TestApparatus {

        val exception = new RuntimeException("some failure")

        (mockHttp.get(_: String)(_: HeaderCarrier, _: ExecutionContext))
          .expects(url, *, *)
          .returning(Future.failed(exception))

        val result = ivConnector.getJourneyStatus(journeyId)

        result.futureValue should be(Some(IvErrorResponse(exception)))
      }
    }
  }
}
