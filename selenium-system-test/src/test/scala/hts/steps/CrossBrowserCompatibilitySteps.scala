/*
 * Copyright 2019 HM Revenue & Customs
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
import hts.pages.accountHomePages.{AccessAccountLink, ChangeEmailPage, NsiManageAccountPage, VerifyEmailPage}
import hts.pages.emailPages.{SelectEmailPage, VerifyYourEmailPage}
import hts.pages.errorPages.NoAccountPage
import hts.pages.informationPages.PrivacyPolicyPage
import hts.pages.registrationPages._
import hts.utils.{ScenarioContext, TestBankDetails}
import org.openqa.selenium.By

class CrossBrowserCompatibilitySteps extends Steps {

  When("^the user logs in and passes IV on a PC, phone or tablet$") {
    AuthorityWizardPage.authenticateEligibleUserOnAnyDevice(CheckEligibilityLink.expectedURL, ScenarioContext.generateEligibleNINO())
  }

  Then("^they go through the happy path$") {
    CheckEligibilityLink.navigate()

    Browser.checkHeader(EligiblePage)
    checkPrivacyPolicyPage(EligiblePage)

    Browser.goBack()
    AccessAccountLink.navigate()

    Browser.checkCurrentPageIs(NoAccountPage)
    NoAccountPage.checkForOldQuotes()
    CheckEligibilityLink.navigate()
    Browser.checkHeader(EligiblePage)

    Browser.scrollToElement("start-creating-account", By.id)
    EligiblePage.continue()

    SelectEmailPage.setAndVerifyNewEmail("newemail@mail.com")
    Browser.checkCurrentPageIs(VerifyYourEmailPage)

    Browser.goBack()
    SelectEmailPage.selectGGEmail()

    Browser.checkCurrentPageIs(BankDetailsPage)
    BankDetailsPage.enterDetails(TestBankDetails.ValidBankDetails)

    Browser.checkCurrentPageIs(CheckDetailsCreateAccountPage)
    CheckDetailsCreateAccountPage.createAccount()

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
    currentPage.navigate()
    Browser.checkCurrentPageIs(currentPage)
  }

}
