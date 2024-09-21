/*
 * Copyright 2023 HM Revenue & Customs
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

import com.typesafe.config.ConfigFactory
import org.mockito.IdiomaticMockito
import org.scalatest.EitherValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.{Application, Configuration}
import uk.gov.hmrc.helptosavefrontend.controllers.ControllerSpecBase
import uk.gov.hmrc.helptosavefrontend.models.iv.IvSuccessResponse.Success
import uk.gov.hmrc.helptosavefrontend.models.iv.{IvErrorResponse, IvUnexpectedResponse, JourneyId}
import uk.gov.hmrc.helptosavefrontend.util.WireMockMethods
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import java.util.UUID
import scala.concurrent.ExecutionContext

// scalastyle:off magic.number
class IvConnectorSpec
    extends ControllerSpecBase with Matchers with ScalaFutures with IdiomaticMockito with WireMockSupport
    with WireMockMethods with GuiceOneAppPerSuite with EitherValues {

  private val config = Configuration(
    ConfigFactory.parseString(
      s"""
         |microservice {
         |  services {
         |      identity-verification-journey-result {
         |      protocol = http
         |      host     = $wireMockHost
         |      port     = $wireMockPort
         |    }
         |  }
         |}
         |""".stripMargin
    )
  )

  override def fakeApplication(): Application = new GuiceApplicationBuilder().configure(config).build()
  val ivConnector: IvConnector = app.injector.instanceOf[IvConnectorImpl]
  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit lazy val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

  class TestApparatus {
    val journeyId = JourneyId(UUID.randomUUID().toString)
    val url = s"/mdtp/journey/journeyId/${journeyId.Id}"
    val emptyBody = "{}"
    val emptyHeaders: Map[String, Seq[String]] = Map.empty
  }

  "The IvConnectorImpl" when {

    "getting Journey Status" should {

      "handle successful response" in new TestApparatus {

        val httpResponse = HttpResponse(200, Json.parse("""{"result": "Success"}"""), emptyHeaders)

        when(GET, url).thenReturn(httpResponse.status, httpResponse.body)

        val result = ivConnector.getJourneyStatus(journeyId)

        result.futureValue shouldBe Some(Success)
      }

      "handle unexpected non-successful response" in new TestApparatus {

        val httpResponse = HttpResponse(600, emptyBody)

        when(GET, url).thenReturn(httpResponse.status, httpResponse.body)

        val result = ivConnector.getJourneyStatus(journeyId)

        result.futureValue match {
          case Some(IvUnexpectedResponse(_)) => ()
          case other                         => fail(s"Expected IvUnexpectedResponse but got $other")
        }
      }

      "handle failure scenarios" in new TestApparatus {
        wireMockServer.stop()
        when(GET, url)

        val result = ivConnector.getJourneyStatus(journeyId)

        result.futureValue match {
          case Some(IvErrorResponse(_)) => ()
          case other                    => fail(s"Expected IvErrorResponse but got $other")
        }
        wireMockServer.start()
      }

    }
  }
}
