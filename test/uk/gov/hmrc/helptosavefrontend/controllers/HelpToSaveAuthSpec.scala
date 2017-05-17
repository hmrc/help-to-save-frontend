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

import akka.util.Timeout
import org.scalamock.scalatest.MockFactory
import org.scalatest.OptionValues
import play.api.http.Status
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import play.api.test.Helpers.redirectLocation
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.AuthorisationException.fromString
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.{HtsAuthRule, UserDetailsUrlWithAllEnrolments}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future
import scala.concurrent.duration._

class HelpToSaveAuthSpec extends UnitSpec with WithFakeApplication with MockFactory with OptionValues {

  private val mockAuthConnector = mock[PlayAuthConnector]

  def mockAuthResultWith(ex: Throwable): Unit =
    (mockAuthConnector.authorise(_: Predicate, _: Retrieval[Unit])(_: HeaderCarrier))
      .expects(AuthProviders(GovernmentGateway), *, *)
      .returning(Future.failed(ex))

  def mockAuthWithRetrievalsWith[A, B](predicate: Predicate, retrieval: Retrieval[A ~ B])(ex: Throwable): Unit =
    (mockAuthConnector.authorise[A ~ B](_: Predicate, _: Retrieval[uk.gov.hmrc.auth.core.~[A, B]])(_: HeaderCarrier))
      .expects(predicate, retrieval, *)
      .returning(Future.failed(ex))

  val htsAuth = new HelpToSaveAuth(fakeApplication) {
    override def authConnector: AuthConnector = mockAuthConnector
  }

  private def actionWithNoEnrols = htsAuth.authorisedForHts {
    implicit request ⇒ Future.successful(Ok(""))
  }

  private def actionWithEnrols = htsAuth.authorisedForHtsWithEnrolments {
    implicit request ⇒
      implicit userWithNino ⇒
        Future.successful(Ok(""))
  }

  private def mockAuthWith(error: String) =
    mockAuthWithRetrievalsWith(HtsAuthRule, UserDetailsUrlWithAllEnrolments)(fromString(error))

  "HelpToSaveAuth" should {

    "handle NoActiveSession exception and redirect user to GG login page" in {

      val exceptions = List(
        "BearerTokenExpired",
        "MissingBearerToken",
        "InvalidBearerToken",
        "SessionRecordNotFound")

      exceptions.foreach { error ⇒
        mockAuthResultWith(fromString(error))
        val result = actionWithNoEnrols(FakeRequest())
        status(result) shouldBe Status.SEE_OTHER
        val redirectTo = redirectLocation(result)(new Timeout(1, SECONDS)).getOrElse("")
        redirectTo should include("/gg/sign-in")
      }
    }

    "handle InsufficientEnrolments exception and display access denied to user" in {

      mockAuthWith("InsufficientEnrolments")

      val result = actionWithEnrols(FakeRequest())
      status(result) shouldBe Status.SEE_OTHER
      val redirectTo = redirectLocation(result)(new Timeout(1, SECONDS)).getOrElse("")
      redirectTo should include("/help-to-save/register/access-denied")
    }

    "handle InsufficientConfidenceLevel exception and redirect user to IV Journey" in {

      mockAuthWith("InsufficientConfidenceLevel")

      val result = actionWithEnrols(FakeRequest())
      status(result) shouldBe Status.SEE_OTHER
      val redirectTo = redirectLocation(result)(new Timeout(1, SECONDS)).getOrElse("")
      redirectTo should include("/mdtp/uplift")
    }

    "handle any other AuthorisationException and redirect user to IV Journey" in {

      mockAuthWith("UnsupportedCredentialRole")

      val result = actionWithEnrols(FakeRequest())
      status(result) shouldBe Status.SEE_OTHER
      val redirectTo = redirectLocation(result)(new Timeout(1, SECONDS)).getOrElse("")
      redirectTo should include("/help-to-save/register/access-denied")
    }

    "handle any other error and display technical error page to user" in {

      mockAuthWith("some other reason")

      val result = actionWithEnrols(FakeRequest())
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR
    }
  }
}
