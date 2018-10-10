/*
 * Copyright 2018 HM Revenue & Customs
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

package stride.pages

import hts.browser.Browser
import hts.pages.{IntroductionHelpToSavePage, Page}
import hts.utils.Configuration
import org.openqa.selenium.WebDriver

object StrideSignInPage extends Page {

  val successURL: String =
    if (Configuration.redirectWithAbsoluteUrls) {
      "http://localhost:7006/help-to-save/hmrc-internal/check-eligibility"
    } else {
      "/help-to-save/hmrc-internal/check-eligibility"
    }

  val expectedURL: String = s"${Configuration.strideAuthFrontendHost}/stride/sign-in?successURL=$successURL&origin=help-to-save-stride-frontend"

  def authenticateOperator()(implicit driver: WebDriver): Unit = {
    navigate()
    fillInStrideDetails()
    clickSubmit()
    Browser.checkCurrentPageIs(IntroductionHelpToSavePage)
  }

  private def fillInStrideDetails()(implicit driver: WebDriver): Unit = {
    setFieldByName("pid", "random-pid")
    setFieldByName("usersGivenName", "test-given-name")
    setFieldByName("usersSurname", "test-surname")
    setFieldByName("emailAddress", "test@hmrc-hts.com")
    setFieldByName("status", "true")
    setFieldByName("signature", "valid")
    setFieldByName("roles", "hts helpdesk advisor")
  }
}
