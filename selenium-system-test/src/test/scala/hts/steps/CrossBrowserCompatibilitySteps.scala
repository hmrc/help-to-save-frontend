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
import hts.utils.{Configuration, ScenarioContext}
import org.openqa.selenium.By

class CrossBrowserCompatibilitySteps extends Steps {

  When("^the user logs in and passes IV on a PC, phone or tablet$") {
    AuthorityWizardPage.authenticateEligibleUserOnAnyDevice(AboutPage.expectedURL, ScenarioContext.generateEligibleNINO())
  }

  Then("^they go through the happy path$") {
    Browser.checkHeader(AboutPage)
    Browser.clickButtonByIdOnceClickable("feedback-link")
    Browser.checkHeader(FeedbackPage)

    Browser.goBack()
    Browser.clickButtonByIdOnceClickable("get-help-action")
    Browser.isElementByIdVisible("report-error-partial-form") shouldBe true

    checkPrivacyPolicyPage(AboutPage)
    Browser.nextPage()

    Browser.checkHeader(EligibilityInfoPage)
    Browser.nextPage()

    Browser.checkHeader(HowTheAccountWorksPage)
    HowTheAccountWorksPage.checkForOldQuotes()
    Browser.nextPage()

    Browser.checkHeader(HowWeCalculateBonusesPage)
    verifyGovUKLink()
    Browser.nextPage()

    Browser.checkHeader(ApplyPage)
    ApplyPage.clickStartNow()

    Browser.checkHeader(EligiblePage)
    Browser.goBack()
    ApplyPage.clickSignInLink()

    Browser.checkCurrentPageIs(YouDoNotHaveAnAccountPage)
    YouDoNotHaveAnAccountPage.checkForOldQuotes()
    Browser.goTo(s"${Configuration.host}/help-to-save/check-eligibility")

    Browser.checkHeader(EligiblePage)
    EligiblePage.detailsNotCorrect()

    Browser.checkCurrentPageIs(IncorrectDetailsPage)
    IncorrectDetailsPage.clickBack

    Browser.checkHeader(EligiblePage)
    Browser.scrollToElement("start-creating-account", By.id)
    EligiblePage.clickConfirmAndContinue()

    SelectEmailPage.setAndVerifyNewEmail("newemail@mail.com")
    Browser.checkCurrentPageIs(VerifyYourEmailPage)

    Browser.goBack()
    SelectEmailPage.selectGGEmail()
    CreateAccountPage.createAccount()

    Browser.checkHeader(NsiManageAccountPage)

    ChangeEmailPage.navigate()
    Browser.checkCurrentPageIs(ChangeEmailPage)
    ChangeEmailPage.checkForOldQuotes()
    ChangeEmailPage.setNewEmailAddress("anotheremail@mail.com")

    Browser.checkCurrentPageIs(VerifyEmailPage)
    VerifyEmailPage.checkForOldQuotes()
    VerifyEmailPage.resendEmail

    Browser.checkHeader(VerifyEmailPage)
    VerifyEmailPage.goToAccountHome

    Browser.checkHeader(NsiManageAccountPage)
  }

  private def checkPrivacyPolicyPage(currentPage: Page): Unit = {
    Browser.openAndCheckPageInNewWindowUsingLinkText("Privacy policy", PrivacyPolicyPage)
    PrivacyPolicyPage.checkForOldQuotes()
    currentPage.navigate()
    Browser.checkCurrentPageIs(currentPage)
  }

  private def verifyGovUKLink(): Unit = {
    Browser.clickButtonByIdOnceClickable("logo")
    Browser.checkCurrentPageIs(GovUKPage)
    Browser.goBack()
  }

}
