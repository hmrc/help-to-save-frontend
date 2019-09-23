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
import hts.pages.accountHomePages._
import hts.pages.emailPages.{CannotChangeEmailPageTryLater, SelectEmailPage, VerifyYourEmailPage}
import hts.pages.errorPages.{MissingInfoPage, NoAccountPage, ServiceUnavailablePage, TotalCapReachedPage}
import hts.pages.govPages.{GGRegistrationPage, GovUKEligibilityPage, GovUKHowItWorksPage}
import hts.pages.informationPages.{DailyCapReachedPage, ThinkYouAreEligiblePage}
import hts.pages.registrationPages._
import hts.utils.{ScenarioContext, TestBankDetails}

class VerifyLinksSteps extends BasePage {

  def checkPage(page: BasePage): Unit = {
    Browser.checkCurrentPageIs(page)
    Browser.checkHeader(page)
    Browser.checkForBadContent(page)
    Browser.checkForLinksThatExistOnEveryPage(page)
  }

  Then("^they create an account and can access all header and footer links$") {
    CheckEligibilityLink.navigate()

    checkPage(EligiblePage)
    EligiblePage.continue()

    checkPage(SelectEmailPage)
    //try to change the email and verify links
    SelectEmailPage.setAndVerifyNewEmail("newemail@mail.com")

    checkPage(VerifyYourEmailPage)
    VerifyYourEmailPage.checkForOldQuotes()

    //go back to original select email page and continue
    Browser.goBack()
    SelectEmailPage.selectGGEmail()

    Browser.checkCurrentPageIs(BankDetailsPage)
    BankDetailsPage.enterDetails(TestBankDetails.ValidBankDetails)

    Browser.checkCurrentPageIs(CheckDetailsCreateAccountPage)
    CheckDetailsCreateAccountPage.createAccount()

    Browser.checkHeader(AccountCreatedPage)
  }

  Then("^they manage their account and can access all header and footer links$") {
    ChangeEmailPage.navigate()
    checkPage(ChangeEmailPage)
    ChangeEmailPage.setNewEmailAddress("anotheremail@mail.com")

    checkPage(VerifyEmailPage)
    VerifyEmailPage.resendEmail

    checkPage(VerifyEmailPage)
    VerifyEmailPage.goToAccountHome

    Browser.checkHeader(NsiManageAccountPage)
  }
  Then("^they can access the cannot change email try later page$") {
    CannotChangeEmailPageTryLater.navigate()
    Browser.checkCurrentPageIs(CannotChangeEmailPageTryLater)
    CannotChangeEmailPageTryLater.checkForOldQuotes()
    Browser.checkForBadContent(CannotChangeEmailPageTryLater)
  }

  Then("^they see the daily cap reached page$") {
    DailyCapReachedPage.navigate()
    Browser.checkCurrentPageIs(DailyCapReachedPage)
    Browser.checkForBadContent(DailyCapReachedPage)
  }

  Then("^they see the total cap reached page$") {
    TotalCapReachedPage.navigate()
    Browser.checkCurrentPageIs(TotalCapReachedPage)
    Browser.checkForBadContent(TotalCapReachedPage)
  }

  Then("^they see the service unavailable page$") {
    ServiceUnavailablePage.navigate()
    Browser.checkCurrentPageIs(ServiceUnavailablePage)
    ServiceUnavailablePage.checkForOldQuotes()
  }

  Then("^they navigate to and see the close account are you sure page$") {
    CloseAccountPage.navigate()
    Browser.checkCurrentPageIs(CloseAccountPage)
    CloseAccountPage.checkForOldQuotes()
    Browser.checkForBadContent(CloseAccountPage)
  }

  When("^an eligible applicant logs into gg$") {
    AuthorityWizardPage.authenticateEligibleUser(AccessAccountLink.expectedURL, ScenarioContext.generateEligibleNINO())
  }

  When("^an applicant logs into gg with missing details$") {
    AuthorityWizardPage.authenticateMissingDetailsUser(AccessAccountLink.expectedURL, ScenarioContext.generateEligibleNINO())
  }

  Then("^they see a page showing which details are missing$") {
    MissingInfoPage.navigate()
    Browser.checkCurrentPageIs(MissingInfoPage)
    MissingInfoPage.checkForOldQuotes()
    Browser.checkForBadContent(MissingInfoPage)
  }

  When("^they click on eligibility for Help to Save link$") {
    Browser.clickLinkTextOnceClickable("eligibility for Help to Save")
  }

  Then("^they are directed to the GOV.UK eligibility page$") {
    Browser.checkCurrentPageIs(GovUKEligibilityPage, "GOV.UK")
  }

  When("^they click on eligibility criteria link$") {
    ThinkYouAreEligiblePage.navigate()
    Browser.clickLinkTextOnceClickable("eligibility criteria")
  }

  When("^they click on Exit to GOV.UK link$") {
    CannotChangeEmailPageTryLater.navigate()
    Browser.clickLinkTextOnceClickable("Exit to GOV.UK")
  }

  Then("^they are directed to the GOV.UK How it works page$") {
    Browser.checkCurrentPageIs(GovUKHowItWorksPage, "GOV.UK")
  }

  When("^they click on Cancel and go to GOV.UK link$") {
    NoAccountPage.navigate()
    Browser.clickLinkTextOnceClickable("Cancel and go to GOV.UK")
  }

  When("^they click on Create a new GG account$") {
    Browser.clickLinkTextOnceClickable("Create a new Government Gateway account")
  }

  Then("^they are directed to the GG registration page$") {
    Browser.checkCurrentPageIs(GGRegistrationPage, "GOV.UK")
  }
}
