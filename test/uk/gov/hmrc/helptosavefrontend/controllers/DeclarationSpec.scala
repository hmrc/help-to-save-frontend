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

import cats.syntax.show._
import org.scalamock.scalatest.MockFactory
import play.api.http.Status
import play.api.i18n.MessagesApi
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavefrontend.connectors.EligibilityConnector
import uk.gov.hmrc.helptosavefrontend.models.UserDetails.localDateShow
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.play.frontend.auth.AuthenticationProviderIds
import uk.gov.hmrc.play.http.{HeaderCarrier, SessionKeys}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import uk.gov.hmrc.time.DateTimeUtils.now

import scala.concurrent.Future

class DeclarationSpec extends UnitSpec with WithFakeApplication with MockFactory {

  val fakeNino = "WM123456C"

  val mockEligibilityConnector: EligibilityConnector = mock[EligibilityConnector]

  val helpToSave = new HelpToSave(fakeApplication.injector.instanceOf[MessagesApi], mockEligibilityConnector) {
    override lazy val authConnector = MockApplicationAuthConnector
  }

  private def authenticatedFakeRequest =
    FakeRequest().withSession(
      SessionKeys.sessionId -> s"session-${UUID.randomUUID()}",
      SessionKeys.lastRequestTimestamp -> now.getMillis.toString,
      SessionKeys.userId -> s"/path/to/authority",
      SessionKeys.authProvider -> AuthenticationProviderIds.VerifyProviderId
    )

  def doRequest(): Future[Result] = helpToSave.declaration(authenticatedFakeRequest)

  def mockEligibilityResult(nino: String, result: Option[UserDetails]): Unit =
    (mockEligibilityConnector.checkEligibility(_: String)(_: HeaderCarrier))
      .expects(nino, *)
      .returning(Future.successful(EligibilityResult(result)))

  "GET /" should {

    "return 200 if the eligibility check is successful" in {
      val user = randomUserDetails()
      mockEligibilityResult(fakeNino, Some(user))

      val result: Future[Result] = doRequest()
      status(result) shouldBe Status.OK

      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")

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
      mockEligibilityResult(fakeNino, None)
      val result = doRequest()
      val html = contentAsString(result)

      html should include("not eligible")
      html should include("To be eligible for an account")
    }
  }
}

