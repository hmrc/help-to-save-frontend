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
import hts.pages.accountHomePages.{ChangeEmailPage, VerifyEmailPage}
import hts.pages.registrationPages._
import hts.utils.ScenarioContext

class VerifyLinksSteps extends Steps {

  Given("^that users are authenticated$") {
    AuthorityWizardPage.authenticateUser(AboutPage.expectedURL, 200, "Strong", ScenarioContext.generateEligibleNINO())
  }

  When("^they are at the start of the hts pages") {
    Browser.checkCurrentPageIs(AboutPage)
  }

  Then("^they see all feedback, get-help and privacy links are working as they go through the journey$") {
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
    verifyGovUKLink()
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
    checkForLinksThatExistOnEveryPage(VerifyYourEmailPage)

    //go back to original select email page and continue
    Browser.goBack()
    SelectEmailPage.selectGGEmail()
    checkForLinksThatExistOnEveryPage(CreateAccountPage)
    CreateAccountPage.createAccount()

    Browser.checkCurrentPageIs(NsiManageAccountPage)

    ChangeEmailPage.navigate()
    Browser.checkCurrentPageIs(ChangeEmailPage)
    checkForLinksThatExistOnEveryPage(ChangeEmailPage)
    ChangeEmailPage.setNewEmailAddress("anotheremail@mail.com")

    Browser.checkCurrentPageIs(VerifyEmailPage)
    checkForLinksThatExistOnEveryPage(VerifyEmailPage)
    VerifyEmailPage.resendEmail

    Browser.checkCurrentPageIs(VerifyEmailPage)
    VerifyEmailPage.goToAccountHome

    Browser.checkCurrentPageIs(NsiManageAccountPage)
  }

  private def checkForLinksThatExistOnEveryPage(currentPage: Page): Unit = {
    Browser.clickButtonByIdOnceClickable("feedback-link")
    Browser.checkCurrentPageIs(FeedbackPage)

    Browser.goBack()
    Browser.clickButtonByIdOnceClickable("get-help-action")
    Browser.isElementByIdVisible("report-error-partial-form") shouldBe true

    Browser.openAndCheckPageInNewWindowUsingLinkText("Privacy policy", PrivacyPolicyPage)

    currentPage.navigate()
    Browser.checkCurrentPageIs(currentPage)
  }

  private def verifyGovUKLink(): Unit = {
    Browser.clickButtonByIdOnceClickable("logo")
    Browser.checkCurrentPageIs(GovUKPage)
    Browser.goBack()
  }

}
