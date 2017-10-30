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
import play.api.http.Status
import play.api.libs.json.Json
import play.api.mvc.Results.{InternalServerError, Ok}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthorisationException.fromString
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig.checkEligibilityUrl
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithCL200
import uk.gov.hmrc.helptosavefrontend.models.{Address, NSIUserInfo, UserInfo}
import uk.gov.hmrc.helptosavefrontend.models.NSIUserInfo.ContactDetails
import uk.gov.hmrc.helptosavefrontend.util.{toJavaDate, urlEncode}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class HelpToSaveAuthSpec extends AuthSupport {

  val htsAuth = new HelpToSaveAuth(mockAuthConnector, mockMetrics)

  private def actionWithNoEnrols = htsAuth.authorisedForHts { implicit request ⇒ implicit htsContext ⇒
    Future.successful(Ok(""))
  }(FrontendAppConfig.checkEligibilityUrl)

  private def actionWithEnrols = htsAuth.authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒
    htsContext.userDetails match {
      case Left(_)         ⇒ Future.successful(InternalServerError(""))
      case Right(userInfo) ⇒ Future.successful(Ok(Json.toJson(userInfo)))
    }
  }(FrontendAppConfig.checkEligibilityUrl)

  private def mockAuthWith(error: String) =
    mockAuthWithRetrievalsWithFail(AuthWithCL200)(fromString(error))

  "HelpToSaveAuth" should {

    "return UserInfo after successful authentication" in {
      val userInfo =
        Json.toJson(UserInfo(
          firstName,
          lastName,
          nino,
          toJavaDate(dob),
          Some(emailStr),
          Address(List(line1, line2, line3), Some(postCode), Some(countryCode)
          )
        ))

      mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)

      val result = Await.result(actionWithEnrols(FakeRequest()), 5.seconds)
      status(result) shouldBe Status.OK
      contentAsJson(result) shouldBe userInfo
    }

    "handle when some user info is missing" in {
      mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsMissingUserInfo)

      val result = Await.result(actionWithEnrols(FakeRequest()), 5.seconds)
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR
    }

    "handle NoActiveSession exception and redirect user to GG login page" in {

      val exceptions = List(
        "BearerTokenExpired",
        "MissingBearerToken",
        "InvalidBearerToken",
        "SessionRecordNotFound")

      exceptions.foreach { error ⇒
        mockAuthResultWithFail(fromString(error))
        val result = actionWithNoEnrols(FakeRequest())
        status(result) shouldBe Status.SEE_OTHER
        val redirectTo = redirectLocation(result)(new Timeout(1, SECONDS)).getOrElse("")
        redirectTo should include("/gg/sign-in")
        redirectTo should include("accountType=individual")
        redirectTo should include(urlEncode(checkEligibilityUrl))
      }
    }

    "handle InsufficientEnrolments exception and redirect user to IV Journey" in {

      mockAuthWith("InsufficientEnrolments")

      val result = actionWithEnrols(FakeRequest())
      status(result) shouldBe Status.SEE_OTHER
      val redirectTo = redirectLocation(result)(new Timeout(1, SECONDS)).getOrElse("")
      redirectTo should include("/mdtp/uplift")
    }

    "handle InsufficientConfidenceLevel exception and redirect user to IV Journey" in {

      mockAuthWith("InsufficientConfidenceLevel")

      val result = actionWithEnrols(FakeRequest())
      status(result) shouldBe Status.SEE_OTHER
      val redirectTo = redirectLocation(result)(new Timeout(1, SECONDS)).getOrElse("")
      redirectTo should include("/mdtp/uplift")
      redirectTo should include("continueURL")
    }

    "handle any other AuthorisationException and display access denied to user" in {

      mockAuthWith("UnsupportedCredentialRole")

      val result = actionWithEnrols(FakeRequest())
      checkIsTechnicalErrorPage(result)
    }

    "handle any other error and display technical error page to user" in {

      mockAuthWith("some other reason")

      val result = actionWithEnrols(FakeRequest())
      checkIsTechnicalErrorPage(result)
    }
  }
}
