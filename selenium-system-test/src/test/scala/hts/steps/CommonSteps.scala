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

package hts.steps

import hts.browser.Browser
import hts.pages.{FeedbackPage, Page, PrivacyPolicyPage}

object CommonSteps extends Steps {

  def checkForLinksThatExistOnEveryPage(currentPage: Page): Unit = {
    Browser.clickButtonByIdOnceClickable("feedback-link")
    Browser.checkCurrentPageIs(FeedbackPage)

    Browser.goBack()
    Browser.clickButtonByIdOnceClickable("get-help-action")
    Browser.isElementByIdVisible("report-error-partial-form") shouldBe true

    Browser.openAndCheckPageInNewWindowUsingLinkText("Privacy policy", PrivacyPolicyPage)

    currentPage.navigate()
    Browser.checkCurrentPageIs(currentPage)
  }

}
