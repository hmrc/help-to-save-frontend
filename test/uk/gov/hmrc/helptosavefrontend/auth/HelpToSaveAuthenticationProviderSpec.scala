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

package uk.gov.hmrc.helptosavefrontend.auth

import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavefrontend.auth.HelpToSaveAuthenticationProvider.redirectToLogin
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

class HelpToSaveAuthenticationProviderSpec extends UnitSpec with WithFakeApplication {

  "redirect to login " should {

    "take the user to gg sign in url" in {

      val fakeRequest = FakeRequest("GET", "/")

      val result = await(redirectToLogin(fakeRequest))
      status(result) shouldBe 303
      val nextURL = redirectLocation(result).getOrElse("")

      nextURL.contains("gg/sign-in") shouldBe true
    }
  }

  "GG account login" should {

    "be of type: individual" in {

      val fakeRequest = FakeRequest("GET", "/")

      val result = await(redirectToLogin(fakeRequest))
      status(result) shouldBe 303
      val nextURL = redirectLocation(result).getOrElse("")

      nextURL.contains("accountType=individual") shouldBe true
    }
  }
}
