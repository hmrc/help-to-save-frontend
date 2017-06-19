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
import uk.gov.hmrc.helptosavefrontend.config.WSHttpExtension
import uk.gov.hmrc.helptosavefrontend.models.iv.IvSuccessResponse.Success
import uk.gov.hmrc.helptosavefrontend.models.iv.{IvErrorResponse, IvUnexpectedResponse, JourneyId}
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpReads, HttpResponse}

import scala.concurrent.Future

class IvConnectorSpec extends TestSupport with ScalaFutures {

  class TestApparatus {
    val httpMock = mock[WSHttpExtension]

    val journeyId = JourneyId(UUID.randomUUID().toString)

    val url = s"http://localhost:9938/mdtp/journey/journeyId/${journeyId.Id}"

    val ivConnector = new IvConnectorImpl {
      override val http: WSHttpExtension = httpMock
    }

    def mockHttpResponse(httpResponse: HttpResponse) = {
      (httpMock.GET[HttpResponse](_: String)(_: HttpReads[HttpResponse], _: HeaderCarrier))
        .expects(url, *, *)
        .returning(httpResponse)
    }
  }

  "The IvConnectorImpl" when {

    "getting Journey Status" should {

      "handle successful response" in new TestApparatus {

        val httpResponse = HttpResponse.apply(200, Some(Json.parse("""{"result": "Success"}""")))

        mockHttpResponse((httpResponse))

        val result = ivConnector.getJourneyStatus(journeyId)

        result.futureValue should be(Some(Success))
      }

      "handle unexpected non-successful response" in new TestApparatus {

        val httpResponse = HttpResponse.apply(600)

        mockHttpResponse((httpResponse))

        val result = ivConnector.getJourneyStatus(journeyId)

        result.futureValue should be(Some(IvUnexpectedResponse(httpResponse)))
      }

      "handle failure scenerios" in new TestApparatus {

        val exception = new RuntimeException("some failure")

        (httpMock.GET[RuntimeException](_: String)(_: HttpReads[RuntimeException], _: HeaderCarrier))
          .expects(url, *, *)
          .returning(Future.failed(exception))

        val result = ivConnector.getJourneyStatus(journeyId)

        result.futureValue should be(Some(IvErrorResponse(exception)))
      }
    }
  }
}
