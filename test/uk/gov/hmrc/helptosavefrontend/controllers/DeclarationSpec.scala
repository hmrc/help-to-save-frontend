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

import cats.syntax.show._
import org.scalamock.scalatest.MockFactory
import play.api.http.Status
import play.api.i18n.MessagesApi
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.helptosavefrontend.connectors.EligibilityConnector
import uk.gov.hmrc.helptosavefrontend.models.UserDetails.localDateShow
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.auth.connectors.domain.{Accounts, Authority, SaAccount}
import uk.gov.hmrc.play.frontend.auth.connectors.domain.ConfidenceLevel.L200
import uk.gov.hmrc.play.frontend.auth.connectors.domain.CredentialStrength.Strong
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class DeclarationSpec extends UnitSpec with WithFakeApplication with MockFactory {

  val user = "user"

  def fakeRequest = FakeRequest("GET", "/").withSession("userId" → user, "token" → "token", "name" → "name")

  val authorisedUser = Authority(user, Accounts(sa = Some(SaAccount("", SaUtr("1234567890")))),
    None, None, Strong, L200, None, None, None, "")

  val mockAuthConnector: AuthConnector = mock[AuthConnector]
  val mockEligibilityConnector: EligibilityConnector = mock[EligibilityConnector]

  val helpToSave = new HelpToSave(fakeApplication.injector.instanceOf[MessagesApi], mockEligibilityConnector){
    override lazy val authConnector = mockAuthConnector
  }

  def doRequest(): Future[Result] = helpToSave.declaration(fakeRequest)

  def mockAuthorisation(): Unit =
    (mockAuthConnector.currentAuthority(_: HeaderCarrier)).expects(*).returning(Future.successful({
      Some(authorisedUser)
    }))

  def mockEligibilityResult(nino: String, result: Option[UserDetails]): Unit =
    (mockEligibilityConnector.checkEligibility(_: String)(_: HeaderCarrier))
      .expects(nino, *)
      .returning(Future.successful(EligibilityResult(result)))


  "GET /" should {

    "call getEligibility from the given EligibilityStubConnector" in {
      mockAuthorisation()
      // this test will fail if the following line is not present - it checks the eligibility
      // connector is actually being called
      mockEligibilityResult(helpToSave.nino, None)
      val result = doRequest()
      Await.result(result, 3.seconds)
    }

    "return 200 if the eligibility check is successful" in {
      mockAuthorisation()
      mockEligibilityResult(helpToSave.nino, Some(randomUserDetails()))

      val result: Future[Result] = doRequest()
      status(result) shouldBe Status.OK
    }

    "return HTML if the eligibility check is successful" in {
      mockAuthorisation()
      mockEligibilityResult(helpToSave.nino, Some(randomUserDetails()))

      val result = doRequest()

      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
    }

    "display the user details if the eligibility check is successful" in {
      val user = randomUserDetails()
      mockAuthorisation()
      mockEligibilityResult(helpToSave.nino, Some(user))

      val result = doRequest()
      val html = contentAsString(result)

      html should include(user.name)
      html should include(user.dateOfBirth.show)
      html should include(user.email)
      html should include(user.NINO)
      html should include(user.phoneNumber)
      html should include(user.address.mkString(","))
      html should include(user.contactPreference.show)
    }

    "display a 'Not Eligible' page if the eligibility check is negative" in {
      mockAuthorisation()
      mockEligibilityResult(helpToSave.nino, None)
      val result = doRequest()
      val html = contentAsString(result)

      html should include("not eligible")
      html should include("To be eligible for an account")
    }


  }
}

