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

import com.google.common.base.Charsets
import com.google.common.io.BaseEncoding
import org.scalamock.scalatest.MockFactory
import play.api.http.Status
import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.helptosavefrontend.config.WSHttpProxy
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.connectors.NSIConnector.{SubmissionFailure, SubmissionSuccess}
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

class NSIConnectorImplSpec extends UnitSpec with WithFakeApplication with MockFactory {

  lazy val mockHTTPProxy = mock[WSHttpProxy]

  lazy val testNSAndIConnectorImpl = new NSIConnectorImpl {
    override val httpProxy = mockHTTPProxy
  }

  implicit val hc = HeaderCarrier()
  implicit val ex = fakeApplication.injector.instanceOf[ExecutionContext]

  val config = fakeApplication.configuration.underlying

  val baseUrl: String = {
    val port = config.getString("microservice.services.nsi.port")
    val host = config.getString("microservice.services.nsi.host")
    s"http://$host:$port"
  }
  val nsiUrlEnd: String = config.getString("microservice.services.nsi.url")
  val url = s"$baseUrl/$nsiUrlEnd"

  def mockCreateAccount[I](body: I)(result: HttpResponse): Unit =
    (mockHTTPProxy.post(
      _: String, _: I, _: Map[String, String]
    )(_: Writes[I], _: HeaderCarrier))
      .expects(url, body, Map.empty[String,String], *, *)
      .returning(Future.successful(result))


  "the createAccount Method" must {
    "Return a SubmissionSuccess when the status is Created" in {
      mockCreateAccount(validNSIUserInfo)(HttpResponse(Status.CREATED))
      val result = testNSAndIConnectorImpl.createAccount(validNSIUserInfo)
      Await.result(result, 3.seconds) shouldBe SubmissionSuccess()
    }

    "Return a SubmissionFailure when the status is BAD_REQUEST" in {
      val submissionFailure = SubmissionFailure(Some("id"), "message", "detail")
      mockCreateAccount(validNSIUserInfo)(HttpResponse(Status.BAD_REQUEST,
        Some(Json.toJson(submissionFailure))))
      val result = testNSAndIConnectorImpl.createAccount(validNSIUserInfo)
      Await.result(result, 3.seconds) shouldBe submissionFailure
    }


    "Return a SubmissionFailure when the status is anything else" in {
      mockCreateAccount(validNSIUserInfo)(HttpResponse(Status.BAD_GATEWAY))
      val result = testNSAndIConnectorImpl.createAccount(validNSIUserInfo)
      Await.result(result, 3.seconds).isInstanceOf[SubmissionFailure] shouldBe true
    }
  }
}