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
import play.api.mvc.{Result => PlayResult}
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentType, _}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosavefrontend.connectors.{CitizenDetailsConnector, EligibilityConnector}
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.services.userinfo.UserInfoService
import uk.gov.hmrc.helptosavefrontend.util.NINO
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.{ExecutionContext, Future}

class RegisterSpec extends UnitSpec with WithFakeApplication with MockFactory {

  implicit val ec: ExecutionContext = fakeApplication.injector.instanceOf[ExecutionContext]

  val fakeNino = "WM123456C"

  val mockEligibilityConnector: EligibilityConnector = mock[EligibilityConnector]

  val authConnector: AuthConnector = mock[AuthConnector]

  val mockUserInfoService: UserInfoService = mock[UserInfoService]

  val mockCitizenDetailsConnector: CitizenDetailsConnector = mock[CitizenDetailsConnector]

  val register = new RegisterController(
    fakeApplication.injector.instanceOf[MessagesApi],
    mockEligibilityConnector,
    mockCitizenDetailsConnector) {
    override val userInfoService = mockUserInfoService

    override def authConnector = authConnector
  }

  def doRequest(): Future[PlayResult] = register.declaration(FakeRequest())

  def mockEligibilityResult(nino: String)(result: Boolean): Unit =
    (mockEligibilityConnector.checkEligibility(_: String)(_: HeaderCarrier))
      .expects(nino, *)
      .returning(EitherT.pure[Future, String, EligibilityResult](EligibilityResult(result)))

  def mockUserInfo(nino: NINO)(userInfo: UserInfo): Unit =
    (mockUserInfoService.getUserInfo(_: Option[String], _: NINO)(_: HeaderCarrier, _: ExecutionContext))
      .expects(Some("/user/details/uri"), nino, *, *)
      .returning(EitherT.pure[Future, String, UserInfo](userInfo))

  "GET /" should {

    "return 200 if the eligibility check is successful" in {
      val user = randomUserDetails()
      inSequence {
        mockUserInfo(fakeNino)(user)
        mockEligibilityResult(fakeNino)(true)
      }

      val result: Future[PlayResult] = doRequest()
      status(result) shouldBe Status.OK

      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")

      val html = contentAsString(result)

      html should include(user.name)
      html should include(user.email)
      html should include(user.NINO)
    }

    "display a 'Not Eligible' page if the eligibility check is negative" in {
      inSequence {
        mockUserInfo(fakeNino)(randomUserDetails())
        mockEligibilityResult(fakeNino)(false)
      }

      val result = doRequest()
      val html = contentAsString(result)

      html should include("not eligible")
      html should include("To be eligible for an account")
    }
    "the getCreateAccountHelpToSave return 200" in {
      val result = register.getCreateAccountHelpToSave(FakeRequest())
      status(result) shouldBe Status.OK
    }

    "the getCreateAccountHelpToSave return HTML" in {
      val result = register.getCreateAccountHelpToSave(FakeRequest())
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
    }
  }
}

