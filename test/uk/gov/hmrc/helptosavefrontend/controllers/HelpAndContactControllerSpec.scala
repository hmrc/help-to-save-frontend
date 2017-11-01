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

import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.http.Status
import play.api.i18n.MessagesApi
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import uk.gov.hmrc.helptosavefrontend.config.{PartialRetriever, WSHttp}
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

class HelpAndContactControllerSpec extends AuthSupport with GeneratorDrivenPropertyChecks {

  val mockHttp: WSHttp = mock[WSHttp]

  val mockFormProvider: PartialRetriever = mock[PartialRetriever]

  lazy val testController = new HelpAndContactController(fakeApplication.injector.instanceOf[MessagesApi],
                                                         mockAuthConnector,
                                                         mockMetrics,
                                                         mockFormProvider,
                                                         mockHttp)(ec)

  val request = mock[RequestHeader]

  def mockPostForm(expectedForm: Map[String, List[String]])(status: Int = Status.OK, response: Option[String] = None): Unit =
    (mockHttp.postForm(_: String, _: Map[String, List[String]], _: Seq[(String, String)])(_: HeaderCarrier, _: ExecutionContext))
      .expects(testController.contactHmrcSubmitPartialUrl, expectedForm, Seq.empty[(String, String)], *, *)
      .returning(Future.successful(HttpResponse(status, responseString = response)))

  def mockGetForm(result: Html): Unit =
    (mockFormProvider.getPartialContent(_: String, _: Map[String, String], _: Html)(_: RequestHeader))
      .expects(testController.contactHmrcFormPartialUrl, *, *, *)
      .returning(result)

  "getHelpAndContactPage" should {

    "return a 200 status and redirect the user to the report a problem page" in {
      inSequence {
        mockAuthWithNoRetrievals(AuthProvider)
        mockGetForm(Html("hello"))
      }

      val result = testController.getHelpAndContactPage(FakeRequest())
      status(result) shouldBe Status.OK
    }
  }

  "submitContactHmrcForm" should {

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

    "return a 400 status when request made to post a form to contact frontend is bad" in {
      val form = Map("key" → List("value1", "value2"))
      inSequence{
        mockAuthWithNoRetrievals(AuthProvider)
        mockPostForm(form)(400, None)
      }

      val result = testController.submitContactHmrcForm(FakeRequest().withFormUrlEncodedBody("key" → "value1",
        "key" → "value2"))
      status(result) shouldBe Status.BAD_REQUEST
    }

    "return a 500 status when there is no form data given" in {
      val form = Map("key" → List(""))
      inSequence{
        mockAuthWithNoRetrievals(AuthProvider)
        mockPostForm(form)(500, None)
      }
      val result = testController.submitContactHmrcForm(FakeRequest().withFormUrlEncodedBody("key" → ""))
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR
    }

    "return the correct status based on a randomly generated response " in {
      forAll { s: Int ⇒
        whenever(!Set(Status.BAD_REQUEST, Status.INTERNAL_SERVER_ERROR, Status.OK).contains(s) && s > 0){
          val form = Map("key" → List(""))
          inSequence {
            mockAuthWithNoRetrievals(AuthProvider)
            mockPostForm(form)(s, None)
          }
          val result = testController.submitContactHmrcForm(FakeRequest().withFormUrlEncodedBody("key" → ""))
          status(result) shouldBe s
        }
      }

    }

    "return an Internal Server Error when there is no form posted" in {
      inSequence{
        mockAuthWithNoRetrievals(AuthProvider)
      }
      val result = testController.submitContactHmrcForm(FakeRequest())
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR
    }
  }

}
