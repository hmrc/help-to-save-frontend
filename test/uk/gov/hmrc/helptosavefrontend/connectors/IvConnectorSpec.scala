/*
 * Copyright 2019 HM Revenue & Customs
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
import uk.gov.hmrc.helptosavefrontend.controllers.ControllerSpecWithGuiceApp
import uk.gov.hmrc.helptosavefrontend.models.iv.IvSuccessResponse.Success
import uk.gov.hmrc.helptosavefrontend.models.iv.{IvErrorResponse, IvUnexpectedResponse, JourneyId}
import uk.gov.hmrc.http.HttpResponse

// scalastyle:off magic.number
class IvConnectorSpec extends ControllerSpecWithGuiceApp with HttpSupport with ScalaFutures {

  class TestApparatus {

    val journeyId = JourneyId(UUID.randomUUID().toString)

    val url = s"${appConfig.ivJourneyResultUrl}/${journeyId.Id}"

    val ivConnector = new IvConnectorImpl(mockHttp)

  }

  "The IvConnectorImpl" when {

    "getting Journey Status" should {

      "handle successful response" in new TestApparatus {

        val httpResponse = HttpResponse(200, Some(Json.parse("""{"result": "Success"}""")))

        mockGet(url)(Some(httpResponse))

        val result = ivConnector.getJourneyStatus(journeyId)

        result.futureValue should be(Some(Success))
      }

      "handle unexpected non-successful response" in new TestApparatus {

        val httpResponse = HttpResponse(600)

        mockGet(url)(Some(httpResponse))

        val result = ivConnector.getJourneyStatus(journeyId)

        result.futureValue should be(Some(IvUnexpectedResponse(httpResponse)))
      }

      "handle failure scenarios" in new TestApparatus {
        mockGet(url)(None)

        val result = ivConnector.getJourneyStatus(journeyId)

        result.futureValue match {
          case Some(IvErrorResponse(_)) ⇒ ()
          case other                    ⇒ fail(s"Expected IvErrorResponse but got $other")
        }
      }

    }
  }
}
