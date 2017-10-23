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

import java.util.UUID

import com.codahale.metrics.{Counter, Histogram, Timer, UniformReservoir}
import com.github.tomakehurst.wiremock.client.WireMock._
import com.kenshoo.play.metrics.Metrics
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.OneAppPerSuite
import play.api.http.Status
import play.api.i18n.MessagesApi
import play.api.mvc._
import play.api.test.FakeRequest
import play.twirl.api.Html
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.AuthProviders
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{EmptyRetrieval, Retrieval}
import uk.gov.hmrc.helptosavefrontend.config.{FormPartialProvider, FrontendAuthConnector, WSHttp, WSHttpExtension}
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth._
import uk.gov.hmrc.helptosavefrontend.util.Crypto
import uk.gov.hmrc.http.{HeaderCarrier, HttpGet, HttpResponse, SessionKeys}
import uk.gov.hmrc.play.frontend.auth.AuthenticationProviderIds
import uk.gov.hmrc.play.partials.{CachedStaticHtmlPartialRetriever, FormPartialRetriever}
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.time.DateTimeUtils

import scala.concurrent.{ExecutionContext, Future}

class HelpAndContactControllerSpec extends AuthSupport with UnitSpec with MockFactory with BeforeAndAfterAll with ScalaFutures {

  val mockHttp: WSHttpExtension = mock[WSHttpExtension]

  implicit val crypto: Crypto = fakeApplication.injector.instanceOf[Crypto]

  lazy val TestController = new HelpAndContactController(fakeApplication.injector.instanceOf[MessagesApi], mockAuthConnector, mockMetrics, mockFormProvider, mockHttp)
  (ec, crypto)
  //    {
  //      implicit val cachedStaticHtmlPartialRetriever: CachedStaticHtmlPartialRetriever = new CachedStaticHtmlPartialRetriever {
  //        override def httpGet: HttpGet = ???
  //
  //        override def getPartialContent(url: String, templateParameters: Map[String, String], errorMessage: Html)(implicit request: RequestHeader): Html =
  //          Html("")
  //      }
  //      implicit val formPartialRetriever: FormPartialRetriever = new FormPartialRetriever {
  //        override def crypto: (String) ⇒ String = ???
  //
  //        override def httpGet: HttpGet = ???
  //
  //        override def getPartialContent(url: String, templateParameters: Map[String, String], errorMessage: Html)(implicit request: RequestHeader): Html = Html("")
  //      }
  //    }

  val mockFormProvider = mock[FormPartialProvider]

  val request = mock[RequestHeader]

  //  private def mockAuthConnectorResult() = {
  //    (mockAuthConnector.authorise[Unit](_: Predicate, _: Retrieval[Unit])(_: HeaderCarrier, _: ExecutionContext))
  //      .expects(AuthProviders(GovernmentGateway), EmptyRetrieval, *, *).returning(Future.successful())
  //  }
  //
  //  private def mockGetPartialContent() = {
  //    when(mockFormProvider.getPartialContent(Matchers.any(), Matchers.any(), Matchers.any())(request))
  //      .thenReturn(Html((HelpAndContactTestViews.views.html.contact_hmrc_test).toString))
  //  }

  def setupMocks(status: Int = Status.OK, response: Option[String] = None): Unit =
    when(mockHttp.POSTForm[HttpResponse](Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), ec))
      .thenReturn(Future.successful(HttpResponse(status, responseString = response)))



  def setupMocks2(status: Int = Status.OK, response: Option[String] = None): Unit =
    (mockHttp.POSTForm[HttpResponse](_: String, _: Map[String, Seq[String]])(httpReads, headerCarrier, ec))
      .expects(*, *, *, *, *)
      .thenReturn(Future.successful(HttpResponse(status, responseString = response)))

  //  private def stubContactFormPartial() {
  //    val formPartial: String = s"""
  //                                 |<h2>Dummy Form</h2>
  //                                 |<form action="/help-to-save/help/problems-with-this-website" method="post">
  //                                 |<input type="hidden" name="csrfToken" value="{{csrfToken}}" />
  //                                 |<button type="submit">Submit</button>
  //                                 |</form>
  //                                 |""".stripMargin
  //    stubFor(get(urlMatching("/contact/contact-hmrc/form.*")).willReturn(aResponse().withStatus(200).withBody(formPartial).withTransformers("csrf-transformer")))
  //}

  "submitContactHmrcForm" should {

    "return a 200 status and redirect the user to the report a problem page" in {

      //mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
      setupMocks(response = Some("1234"))

      showWithSessionAndAuth(TestController.getHelpAndContactPage)(
        res ⇒ status(res) shouldBe Status.OK)

      //result.body should contain("/help-to-save/help/problems-with-this-website")

      //charset(result) shouldBe Some("utf-8")
      //contentAsString(result) should include()
      //contentAsString(result) should include()
    }

    "return a 200 status and redirect an authenticated user to the contactHmrcThankYou page" ignore {
      //mockAuthorise(true)

      mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)

      //val result = htsHelpController.submitContactHmrcForm(FakeRequest("POST", "/help/ticket-submission"))
      //status(result) shouldBe Status.OK

    }
  }

  def showWithSessionAndAuth(action: Action[AnyContent])(test: Future[Result] ⇒ Any) {
    val result = action.apply(authorisedFakeRequest)
    test(result)
  }

  lazy val authorisedFakeRequest = authenticatedFakeRequest()

  def authenticatedFakeRequest(provider: String = AuthenticationProviderIds.GovernmentGatewayId,
                               userId:   String = mockUserId): FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest().withSession(
      SessionKeys.userId -> userId,
      SessionKeys.sessionId -> s"session-${UUID.randomUUID()}",
      SessionKeys.lastRequestTimestamp -> DateTimeUtils.now.getMillis.toString,
      SessionKeys.token -> "ANYOLDTOKEN",
      SessionKeys.authProvider -> provider
    )

  val mockUsername = "mockuser"
  val mockUserId = "/auth/oid/" + mockUsername

}
