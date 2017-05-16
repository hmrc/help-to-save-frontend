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

import cats.data.EitherT
import cats.instances.future._
import org.scalamock.scalatest.MockFactory
import play.api.http.Status
import play.api.i18n.MessagesApi
import play.api.libs.json.{JsValue, Reads, Writes}
import play.api.mvc.{Result ⇒ PlayResult}
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentType, _}
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.helptosavefrontend.connectors.CreateAccountConnector.SubmissionResult
import uk.gov.hmrc.helptosavefrontend.connectors._
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.{HtsAuthRule, UserDetailsUrlWithAllEnrolments}
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.NINO
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.ws.WSPost
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class RegisterControllerSpec extends UnitSpec with WithFakeApplication with MockFactory {

  implicit val ec: ExecutionContext = fakeApplication.injector.instanceOf[ExecutionContext]

  private val mockHtsService = mock[HelpToSaveService]

  private val mockHTTPPost = mock[WSPost]

  val uDetailsUri = "/dummy/user/details/uri"
  val nino = "WM123456C"

  private val enrolment = Enrolment("HMRC-NI", Seq(EnrolmentIdentifier("NINO", nino)), "activated", ConfidenceLevel.L200)
  private val enrolments = Enrolments(Set(enrolment))

  val mockAuthConnector = mock[PlayAuthConnector]
  val mockSessionCacheConnector: SessionCacheConnector = mock[SessionCacheConnector]
  val mockEligibilityConnector: EligibilityConnector = mock[EligibilityConnector]
  val mockCitizenDetailsConnector: CitizenDetailsConnector = mock[CitizenDetailsConnector]
  val mockCreateAccountConnector: CreateAccountConnector = mock[CreateAccountConnector]

  val register = new RegisterController(
    fakeApplication.injector.instanceOf[MessagesApi],
    mockHtsService,
    mockCreateAccountConnector,
    mockSessionCacheConnector,
    fakeApplication) {
    override lazy val authConnector = mockAuthConnector
  }

  def doRequest(): Future[PlayResult] = register.declaration(FakeRequest())

  def mockUserInfo(uDetailsUri: String, nino: NINO)(userInfo: UserInfo): Unit =
    (mockHtsService.getUserInfo(_: String, _: NINO)(_: HeaderCarrier, _: ExecutionContext))
      .expects(uDetailsUri, nino, *, *)
      .returning(EitherT.pure[Future, String, UserInfo](userInfo))

  def mockEligibilityResult(nino: String)(result: Boolean): Unit =
    (mockHtsService.checkEligibility(_: String)(_: HeaderCarrier))
      .expects(nino, *)
      .returning(EitherT.pure[Future, String, EligibilityResult](EligibilityResult(result)))

  def mockSessionCacheConnectorPut(cacheMap: CacheMap): Unit =
    (mockSessionCacheConnector.put(_: HTSSession)(_: Writes[HTSSession], _: HeaderCarrier))
      .expects(*, *, *)
      .returning(Future.successful(cacheMap))

  def mockSessionCacheConnectorGet(mockHtsSession: Option[HTSSession]): Unit =
    (mockSessionCacheConnector.get(_: HeaderCarrier, _: Reads[HTSSession]))
      .expects(*, *)
      .returning(Future.successful(mockHtsSession))

  def mockCreateAccount(nsiResponse: SubmissionResult): Unit =
    (mockCreateAccountConnector.createAccount(_: UserInfo)(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *)
      .returning(Future.successful(nsiResponse))

  def mockPlayAuthWithRetrievals[A, B](predicate: Predicate, retrieval: Retrieval[A ~ B])(result: A ~ B): Unit =
    (mockAuthConnector.authorise[A ~ B](_: Predicate, _: Retrieval[uk.gov.hmrc.auth.core.~[A, B]])(_: HeaderCarrier))
      .expects(predicate, retrieval, *)
      .returning(Future.successful(result))

  def mockPlayAuthWithNoRetrievals(): Unit =
    (mockAuthConnector.authorise(_: Predicate, _: Retrieval[Unit])(_: HeaderCarrier))
      .expects(AuthProviders(GovernmentGateway), *, *)
      .returning(Future.successful(()))

  "GET /" should {

    "return user details if the user is eligible for help-to-save" in {
      val user = randomUserDetails()
      inSequence {
        mockPlayAuthWithRetrievals(HtsAuthRule, UserDetailsUrlWithAllEnrolments)(uk.gov.hmrc.auth.core.~(Some("/dummy/user/details/uri"), enrolments))
        mockUserInfo(uDetailsUri, nino)(user)
        mockEligibilityResult(nino)(result = true)
        mockSessionCacheConnectorPut(CacheMap("1", Map.empty[String, JsValue]))
      }

      val responseFuture: Future[PlayResult] = doRequest()
      val result = Await.result(responseFuture, 3.seconds)

      status(result) shouldBe Status.OK

      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")

      val html = contentAsString(result)

      html should include(user.forename)
      html should include(user.email)
      html should include(user.NINO)
    }

    "display a 'Not Eligible' page if the user is not eligible" in {
      inSequence {
        mockPlayAuthWithRetrievals(HtsAuthRule, UserDetailsUrlWithAllEnrolments)(uk.gov.hmrc.auth.core.~(Some("/dummy/user/details/uri"), enrolments))
        mockUserInfo(uDetailsUri, nino)(randomUserDetails())
        mockEligibilityResult(nino)(result = false)
      }

      val result = doRequest()
      val html = contentAsString(result)

      html should include("not eligible")
      html should include("To be eligible for an account")
    }

    "the getCreateAccountHelpToSave return 200" in {
      mockPlayAuthWithNoRetrievals()
      val result = register.getCreateAccountHelpToSave(FakeRequest())
      status(result) shouldBe Status.OK
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
    }
  }
}
