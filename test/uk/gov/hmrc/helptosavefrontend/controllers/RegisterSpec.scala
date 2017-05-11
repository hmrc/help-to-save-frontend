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

import java.time.LocalDate

import cats.data.EitherT
import cats.instances.future._
import org.scalamock.scalatest.MockFactory
import play.api.http.Status
import play.api.i18n.MessagesApi
import play.api.libs.json.{JsValue, Reads, Writes}
import play.api.mvc.{Result => PlayResult}
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentType, _}
import uk.gov.hmrc.helptosavefrontend.connectors.NSIConnector.{SubmissionFailure, SubmissionResult, SubmissionSuccess}
import uk.gov.hmrc.helptosavefrontend.connectors._
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.services.userinfo.UserInfoService
import uk.gov.hmrc.helptosavefrontend.services.userinfo.UserInfoService.UserDetailsResponse
import uk.gov.hmrc.helptosavefrontend.util.NINO
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.{ExecutionContext, Future}

class RegisterSpec extends UnitSpec with WithFakeApplication with MockFactory {

  implicit val ec: ExecutionContext = fakeApplication.injector.instanceOf[ExecutionContext]

  val fakeNino = "WM123456C"

  val mockHtsSession: HTSSession = HTSSession(Some(validUserInfo))

  val mockEligibilityConnector: EligibilityConnector = mock[EligibilityConnector]
  val mockUserInfoService: UserInfoService = mock[UserInfoService]
  val mockNsAndIConnector: NSIConnector = mock[NSIConnector]
  val mockCitizenDetailsConnector: CitizenDetailsConnector = mock[CitizenDetailsConnector]
  val mockSessionCacheConnector: SessionCacheConnector = mock[SessionCacheConnector]

  val register = new RegisterController(
    mockSessionCacheConnector,
    fakeApplication.injector.instanceOf[MessagesApi],
    mockEligibilityConnector,
    mockCitizenDetailsConnector,
    mockNsAndIConnector) {
    override val userInfoService = mockUserInfoService
  }

  private def authenticatedFakeRequest = FakeRequest()

  def doRequest(): Future[PlayResult] = register.declaration(authenticatedFakeRequest)

  def mockSessionCacheConnectorPut(cacheMap: CacheMap): Unit =
    (mockSessionCacheConnector.put(_: HTSSession)(_: Writes[HTSSession], _: HeaderCarrier))
      .expects(*, *, *)
      .returning(Future.successful(cacheMap))

  def mockSessionCacheConnectorGet(mockHtsSession: Option[HTSSession]): Unit =
    (mockSessionCacheConnector.get(_: HeaderCarrier, _: Reads[HTSSession]))
      .expects(*, *)
      .returning(Future.successful(mockHtsSession))

  def mockEligibilityResult(nino: String)(result: Boolean): Unit =
    (mockEligibilityConnector.checkEligibility(_: String)(_: HeaderCarrier))
      .expects(nino, *)
      .returning(EitherT.pure[Future, String, EligibilityResult](EligibilityResult(result)))

  def mockUserInfo(userDetailsResponse: UserDetailsResponse, nino: NINO)(userInfo: UserInfo): Unit =
    (mockUserInfoService.getUserInfo(_: UserDetailsResponse, _: NINO)(_: HeaderCarrier, _: ExecutionContext))
      .expects(userDetailsResponse, nino, *, *)
      .returning(EitherT.pure[Future, String, UserInfo](userInfo))

  def mockCreateAccount(nsiResponse: SubmissionResult): Unit =
    (mockNsAndIConnector.createAccount(_: NSIUserInfo)(_: HeaderCarrier, _: ExecutionContext))
      .expects(validNSIUserInfo, *, *)
      .returning(Future.successful(nsiResponse))

  val userDetailsResponse = UserDetailsResponse("test", Some("last"), Some("test@test.com"), Some(LocalDate.now()))

  "GET /register/declaration " should {

    "return 200 if the eligibility check is successful" in {
      val user = randomUserDetails()
      inSequence {
        mockUserInfo(userDetailsResponse, fakeNino)(user)
        mockEligibilityResult(fakeNino)(true)
        mockSessionCacheConnectorPut(CacheMap("1", Map.empty[String, JsValue]))
      }

      val result: Future[PlayResult] = doRequest()
      status(result) shouldBe Status.OK

      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")

      val html = contentAsString(result)

      html should include(user.forename)
      html should include(user.surname)
      html should include(user.email)
      html should include(user.NINO)
      html should include(user.address.line1.getOrElse(sys.error("Could not get first line of address")))

    }

    "display a 'Not Eligible' page if the eligibility check is negative" in {
      inSequence {
        mockUserInfo(userDetailsResponse, fakeNino)(randomUserDetails())
        mockEligibilityResult(fakeNino)(false)
      }

      val result = doRequest()
      val html = contentAsString(result)

      html should include("not eligible")
      html should include("To be eligible for an account")
    }
  }
  "GET  /register/create-a-account" should {
    "the getCreateAccountHelpToSave return 200" in {
      val result = register.getCreateAccountHelpToSave(authenticatedFakeRequest)
      status(result) shouldBe Status.OK
    }

    "the getCreateAccountHelpToSave return HTML" in {
      val result = register.getCreateAccountHelpToSave(authenticatedFakeRequest)
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
    }
  }
  "POST /register/create-a-account  " should {
    "the postCreateAccountHelpToSave return the nsi stub in" in {
      mockSessionCacheConnectorGet(Some(mockHtsSession))
      mockCreateAccount(SubmissionSuccess)
      val result = register.postCreateAccountHelpToSave(authenticatedFakeRequest)
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")

      val html = contentAsString(result)

      html should include("This is a stub for nsi")
    }
    "the postCreateAccountHelpToSave return the errors if ns and I fail in" in {
      mockSessionCacheConnectorGet(Some(mockHtsSession))
      mockCreateAccount(SubmissionFailure(Some("401"), "Fail", "Fail"))
      val result = register.postCreateAccountHelpToSave(authenticatedFakeRequest)
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")

      val html = contentAsString(result)

      html should include("Fail")
    }

    "the postCreateAccountHelpToSave return the errors if the session cache returns none" in {
      mockSessionCacheConnectorGet(None)

      val result = register.postCreateAccountHelpToSave(authenticatedFakeRequest)
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")

      val html = contentAsString(result)

      html should include("Session cache did not contain user info")
    }

    "the postCreateAccountHelpToSave return the errors if the UserInfo Is Invalid" in {
      mockSessionCacheConnectorGet(
        Some(mockHtsSession.copy(userInfo = Some(validUserInfo.copy(email = "")))))

      val result = register.postCreateAccountHelpToSave(authenticatedFakeRequest)
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")

      val html = contentAsString(result)

      html should include("Invalid user details")
    }
  }
}

