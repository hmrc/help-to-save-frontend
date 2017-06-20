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

import org.scalamock.scalatest.MockFactory
import play.api.http.Status
import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig.{authDetails, authHeaderKey, nsiUrl}
import uk.gov.hmrc.helptosavefrontend.config.WSHttpProxy
import uk.gov.hmrc.helptosavefrontend.connectors.NSIConnector.{SubmissionFailure, SubmissionSuccess}
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.play.http.logging.Authorization
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class NSIConnectorSpec extends TestSupport with MockFactory {

  lazy val mockHTTPProxy = mock[WSHttpProxy]

  lazy val testNSAndIConnectorImpl = new NSIConnectorImpl {
    override val httpProxy = mockHTTPProxy
  }

  // put in fake authorization details - these should be removed by the call to create an account
  implicit val hc = HeaderCarrier(authorization = Some(Authorization("auth")))

  def mockCreateAccount[I](body: I)(result: HttpResponse): Unit =
    (mockHTTPProxy.post(
      _: String, _: I, _: Map[String, String]
    )(_: Writes[I], _: HeaderCarrier))
      .expects(nsiUrl, body, Map(authHeaderKey → authDetails), *, hc.copy(authorization = None))
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

    "Return a SubmissionFailure in case there is an invalid json" in {
      mockCreateAccount(validNSIUserInfo)(HttpResponse(Status.BAD_REQUEST,
        Some(Json.parse("""{"invalidJson":"foo"}"""))))
      val result = testNSAndIConnectorImpl.createAccount(validNSIUserInfo)
      Await.result(result, 3.seconds).isInstanceOf[SubmissionFailure] shouldBe true
    }

    "Return a SubmissionFailure when the status is anything else" in {
      mockCreateAccount(validNSIUserInfo)(HttpResponse(Status.BAD_GATEWAY))
      val result = testNSAndIConnectorImpl.createAccount(validNSIUserInfo)
      Await.result(result, 3.seconds).isInstanceOf[SubmissionFailure] shouldBe true
    }
  }
}