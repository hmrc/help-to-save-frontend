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

package uk.gov.hmrc.helptosavefrontend.controllers

import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import play.api.http.Status
import play.api.i18n.MessagesApi
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import uk.gov.hmrc.helptosavefrontend.config.{PartialRetriever, WSHttp}
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

class HelpAndContactControllerSpec extends AuthSupport with UnitSpec with MockFactory with BeforeAndAfterAll with ScalaFutures {

  val mockHttp: WSHttp = mock[WSHttp]

  val mockFormProvider: PartialRetriever = mock[PartialRetriever]

  lazy val testController = new HelpAndContactController(fakeApplication.injector.instanceOf[MessagesApi], mockAuthConnector, mockMetrics, mockFormProvider, mockHttp)
  (ec)

  val request = mock[RequestHeader]

  def mockPostForm(expectedForm: Map[String, List[String]])(status: Int = Status.OK, response: Option[String] = None): Unit =
    (mockHttp.postForm(_: String, _: Map[String, List[String]], _: Seq[(String, String)])(_: HeaderCarrier, _: ExecutionContext))
      .expects(testController.contactHmrcSubmitPartialUrl, expectedForm, Seq.empty[(String, String)], *, *)
      .returning(Future.successful(HttpResponse(status, responseString = response)))

  def mockGetForm(result: Html) =
    (mockFormProvider.getPartialContent(_: String, _: Map[String, String], _: Html)(_: RequestHeader))
      .expects(testController.contactHmrcFormPartialUrl, *, *, *)
      .returning(result)

  "submitContactHmrcForm" should {

    "return a 200 status and redirect the user to the report a problem page" in {
      inSequence {
        mockAuthWithNoRetrievals(AuthProvider)
        mockGetForm(Html("hello"))
      }

      val result = testController.getHelpAndContactPage(FakeRequest())
      status(result) shouldBe Status.OK
    }

    "return a 200 status and redirect an authenticated user to the contactHmrcThankYou page" in {
      val form = Map("key" → List("value1", "value2"))

      inSequence{
        mockAuthWithNoRetrievals(AuthProvider)
        mockPostForm(form)(200, Some("1234"))
      }

      val result = testController.submitContactHmrcForm(FakeRequest().withFormUrlEncodedBody("key" → "value1",
        "key" → "value2"))
      redirectLocation(result) shouldBe Some(routes.HelpAndContactController.contactHmrcThankYou().url)
    }

    "return a 500 status when there is no form data given" in {

    }
  }


}
