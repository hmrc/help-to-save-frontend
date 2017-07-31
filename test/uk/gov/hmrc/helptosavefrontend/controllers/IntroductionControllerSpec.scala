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

import java.util.concurrent.TimeUnit.SECONDS

import akka.util.Timeout
import play.api.http.Status
import play.api.i18n.MessagesApi
import play.api.test.FakeRequest
import play.api.test.Helpers.{charset, contentAsString, contentType}
import uk.gov.hmrc.helptosavefrontend.TestSupport

class IntroductionControllerSpec extends TestSupport {

  implicit val timeout = Timeout(5, SECONDS)

  val fakeRequest = FakeRequest("GET", "/")
  val helpToSave = new IntroductionController()(fakeApplication, fakeApplication.injector.instanceOf[MessagesApi])

  "GET /" should {
    "the getAboutHelpToSave should return html" in {
      val result = helpToSave.getAboutHelpToSave(fakeRequest)
      status(result) shouldBe Status.OK

      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")

      val html = contentAsString(result)

      html should not include "Sign out"
    }

    "the getEligibility should  return 200" in {
      val result = helpToSave.getEligibility(fakeRequest)
      status(result) shouldBe Status.OK

      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
    }

    "the getHowTheAccountWorks return 200" in {
      val result = helpToSave.getHowTheAccountWorks(fakeRequest)
      status(result) shouldBe Status.OK

      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
    }

    "the getHowWeCalculateBonuses return 200" in {
      val result = helpToSave.getHowWeCalculateBonuses(fakeRequest)
      status(result) shouldBe Status.OK

      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
    }

    "the getApply return 200" in {
      val result = helpToSave.getApply(fakeRequest)
      status(result) shouldBe Status.OK

      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
    }
  }
}
