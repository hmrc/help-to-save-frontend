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
import hts.pages._
import hts.pages.registrationPages._
import hts.utils.ScenarioContext

import scala.collection.JavaConverters._

class VerifyLinksSteps extends Steps {

  Given("""^that users are authenticated$""") { () ⇒
    AuthorityWizardPage.authenticateUser(AboutPage.expectedURL, 200, "Strong", ScenarioContext.generateEligibleNINO())
  }

  When("""^they are at the start of the hts pages""") { () ⇒
    Browser.checkCurrentPageIs(AboutPage)
  }

  Then("""^they see all feedback, get-help and privacy links are working as they go through the journey$""") { () ⇒
    Browser.checkCurrentPageIs(AboutPage)
    checkForLinksThatExistOnEveryPage(AboutPage)
    Browser.nextPage()

    Browser.checkCurrentPageIs(EligibilityInfoPage)
    checkForLinksThatExistOnEveryPage(EligibilityInfoPage)
    Browser.nextPage()

    Browser.checkCurrentPageIs(HowTheAccountWorksPage)
    checkForLinksThatExistOnEveryPage(HowTheAccountWorksPage)
    Browser.nextPage()

    Browser.checkCurrentPageIs(HowWeCalculateBonusesPage)
    checkForLinksThatExistOnEveryPage(HowWeCalculateBonusesPage)
    Browser.nextPage()

    Browser.checkCurrentPageIs(ApplyPage)
    checkForLinksThatExistOnEveryPage(ApplyPage)

    ApplyPage.clickStartNow()

    Browser.checkCurrentPageIs(EligiblePage)
    checkForLinksThatExistOnEveryPage(EligiblePage)

    EligiblePage.clickConfirmAndContinue()
    checkForLinksThatExistOnEveryPage(SelectEmailPage)

    //try to change the email and verify links
    SelectEmailPage.setAndVerifyNewEmail("newemail@mail.com")
    Browser.checkCurrentPageIs(VerifyYourEmailPage)
    checkForLinksThatExistOnEveryPage(SelectEmailPage)

    //go back to original select email page and continue
    SelectEmailPage.selectGGEmail()
    checkForLinksThatExistOnEveryPage(CreateAccountPage)

  }

  private def checkForLinksThatExistOnEveryPage(currentPage: Page): Unit = {

    verifyFeedbackLink()
    driver.navigate().back()
    verifyGetHelpLink()
    verifyPrivacyPolicyLink()

    currentPage.navigate()
    Browser.checkCurrentPageIs(currentPage)
  }

  private def verifyFeedbackLink(): Unit = {
    Browser.isTextOnPage("BETA") shouldBe true
    Browser.clickButtonByIdOnceClickable("feedback-link")
    Browser.checkCurrentPageIs(FeedbackPage)
  }

  private def verifyGetHelpLink(): Unit = {
    Browser.isTextOnPage("Is there anything wrong with this page") shouldBe true
    Browser.clickButtonByIdOnceClickable("get-help-action")
    Browser.isElementByIdVisible("report-error-partial-form") shouldBe true

  }

  private def verifyPrivacyPolicyLink(): Unit = {
    Browser.isTextOnPage("Privacy policy") shouldBe true
    Browser.clickLinkTextOnceClickable("Privacy policy")
    val tabs = driver.getWindowHandles.asScala.toList
    tabs match {
      case tab1 :: tab2 :: Nil ⇒
        driver.switchTo.window(tab2) //switch to privacy policy tab
        Browser.checkCurrentPageIs(PrivacyPolicyPage)
        driver.close()
        driver.switchTo.window(tab1) //switch back to original page
      case ts ⇒ fail(s"Unexpected number of tabs: ${ts.length}")
    }

  }

}
