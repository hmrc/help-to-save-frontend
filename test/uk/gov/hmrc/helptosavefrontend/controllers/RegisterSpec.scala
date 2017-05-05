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

import cats.data.EitherT
import cats.syntax.show._
import cats.instances.future._
import org.scalamock.scalatest.MockFactory
import play.api.http.Status
import play.api.i18n.MessagesApi
import play.api.mvc.{Result ⇒ PlayResult}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.domain.Nino
import play.api.test.Helpers.{contentType, _}
import uk.gov.hmrc.helptosavefrontend.connectors.{CitizenDetailsConnector, EligibilityConnector}
import uk.gov.hmrc.helptosavefrontend.models.UserInfo.localDateShow
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.services.userinfo.UserInfoService
import uk.gov.hmrc.helptosavefrontend.util.{NINO, Result}
import uk.gov.hmrc.play.frontend.auth.{AuthContext, AuthenticationProviderIds}
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.auth.connectors.domain._
import uk.gov.hmrc.play.http.{HeaderCarrier, SessionKeys}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import uk.gov.hmrc.time.DateTimeUtils.now

import scala.concurrent.{ExecutionContext, Future}

class RegisterSpec extends UnitSpec with WithFakeApplication with MockFactory {

  implicit val ec: ExecutionContext = fakeApplication.injector.instanceOf[ExecutionContext]

  val fakeNino = "WM123456C"

  val authority = Authority(uri = s"/path/to/authority",
    accounts = Accounts(
      paye = Some(PayeAccount(s"/taxcalc/$fakeNino", Nino(fakeNino))),
      tai = Some(TaxForIndividualsAccount(s"/tai/$fakeNino", Nino(fakeNino)))),
    loggedInAt = None,
    previouslyLoggedInAt = None,
    credentialStrength = CredentialStrength.Strong,
    confidenceLevel = ConfidenceLevel.L200,
    userDetailsLink = Some("/user-details/mockuser"),
    enrolments = Some("/auth/oid/mockuser/enrolments"),
    ids = Some("/auth/oid/mockuser/ids"),
    legacyOid = "mockuser")

  val authContext = AuthContext(authority)

  val mockEligibilityConnector: EligibilityConnector = mock[EligibilityConnector]

  val mockAuthConnector: AuthConnector = mock[AuthConnector]

  val mockUserInfoService: UserInfoService = mock[UserInfoService]

  val mockCitizenDetailsConnector: CitizenDetailsConnector = mock[CitizenDetailsConnector]

  val register = new Register(
    fakeApplication.injector.instanceOf[MessagesApi],
    mockEligibilityConnector,
    mockCitizenDetailsConnector){
    override lazy val authConnector = mockAuthConnector
    override val userInfoService = mockUserInfoService

  }

  private def authenticatedFakeRequest =
    FakeRequest().withSession(
      SessionKeys.sessionId -> s"session-${UUID.randomUUID()}",
      SessionKeys.lastRequestTimestamp -> now.getMillis.toString,
      SessionKeys.userId -> s"/path/to/authority",
      SessionKeys.authProvider -> AuthenticationProviderIds.VerifyProviderId
    )

  def doRequest(): Future[PlayResult] = register.declaration(authenticatedFakeRequest)

  def mockEligibilityResult(nino: String)(result: Boolean): Unit =
    (mockEligibilityConnector.checkEligibility(_: String)(_: HeaderCarrier))
      .expects(nino, *)
      .returning(EitherT.pure[Future,String,EligibilityResult](EligibilityResult(result)))

  def mockAuthConnector(authority: Authority): Unit =
    (mockAuthConnector.currentAuthority(_: HeaderCarrier))
      .expects(*)
      .returning(Future.successful(Some(authority)))

  def mockUserInfo(authContext: AuthContext, nino: NINO)(userInfo: UserInfo): Unit =
    (mockUserInfoService.getUserInfo(_: AuthContext, _: NINO)(_: HeaderCarrier, _: ExecutionContext))
      .expects(authContext, nino, *, *)
      .returning(EitherT.pure[Future,String,UserInfo](userInfo))

  "GET /" should {

    "return 200 if the eligibility check is successful" in {
      val user = randomUserDetails()
      inSequence {
        mockAuthConnector(authority)
        mockUserInfo(authContext, fakeNino)(user)
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
      inSequence{
        mockAuthConnector(authority)
        mockUserInfo(authContext, fakeNino)(randomUserDetails())
        mockEligibilityResult(fakeNino)(false)
      }

      val result = doRequest()
      val html = contentAsString(result)

      html should include("not eligible")
      html should include("To be eligible for an account")
    }
    "the getCreateAccountHelpToSave return 200" in {
      mockAuthConnector(authority)
      val result = register.getCreateAccountHelpToSave(authenticatedFakeRequest)
      status(result) shouldBe Status.OK
    }

    "the getCreateAccountHelpToSave return HTML" in {
      mockAuthConnector(authority)
      val result = register.getCreateAccountHelpToSave(authenticatedFakeRequest)
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
    }
  }
}

