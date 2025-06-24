/*
 * Copyright 2025 HM Revenue & Customs
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

import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.mvc.MessagesControllerComponents
import play.api.test.Helpers.*
import play.api.test.FakeRequest
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.play.language.LanguageUtils
import play.api.i18n.Lang

class CustomLanguageControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  "CustomLanguageController" should {

    "successfully switch to English" in {
      val controller = createController()
      val result = controller.switchToEnglish()(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(controller.fallbackURL)
      cookies(result).get("PLAY_LANG").map(_.value) shouldBe Some("en")
    }

    "successfully switch to Welsh" in {
      val controller = createController()
      val result = controller.switchToWelsh()(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(controller.fallbackURL)
      cookies(result).get("PLAY_LANG").map(_.value) shouldBe Some("cy")
    }

    "return the correct fallback URL" in {
      val controller = createController()
      controller.fallbackURL shouldBe controller.frontendAppConfig.helpToSaveFrontendUrl
    }

    "return the correct language map" in {
      val controller = createController()
      controller.languageMap shouldBe Map("en" -> Lang("en"), "cy" -> Lang("cy"))
    }
  }

  private def createController(): CustomLanguageController = {
    val languageUtils = app.injector.instanceOf[LanguageUtils]
    val mcc = app.injector.instanceOf[MessagesControllerComponents]
    val config = app.injector.instanceOf[FrontendAppConfig]

    new CustomLanguageController(languageUtils, mcc)(config)
  }
}
