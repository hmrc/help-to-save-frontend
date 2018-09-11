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

  def checkPage(page: Page): Unit = {
    Browser.checkCurrentPageIs(page)
    Browser.checkHeader(page)
    Browser.checkForBadContent(page)
    Browser.checkForLinksThatExistOnEveryPage(page)
  }

  Then("^they go through the happy path they can see and access all header and footer links$") {
    AboutPage.navigate()
    checkPage(AboutPage)
    Browser.nextPage()

    checkPage(EligibilityInfoPage)
    EligibilityInfoPage.checkForOldQuotes()
    Browser.nextPage()

    checkPage(HowTheAccountWorksPage)
    Browser.nextPage()

    checkPage(HowWeCalculateBonusesPage)
    HowWeCalculateBonusesPage.checkForOldQuotes()
    verifyGovUKLink()
    Browser.nextPage()

    checkPage(ApplyPage)
    ApplyPage.clickStartNow()

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
    BankDetailsPage.enterValidDetails()

    Browser.checkCurrentPageIs(CheckYourDetailsPage)
    CheckYourDetailsPage.continue()

    checkPage(CreateAccountPage)
    CreateAccountPage.createAccount()

    Browser.checkHeader(NsiManageAccountPage)

    ChangeEmailPage.navigate()
    checkPage(ChangeEmailPage)
    ChangeEmailPage.setNewEmailAddress("anotheremail@mail.com")

    checkPage(VerifyEmailPage)
    VerifyEmailPage.resendEmail

    checkPage(VerifyEmailPage)
    VerifyEmailPage.goToAccountHome

    Browser.checkHeader(NsiManageAccountPage)
  }

  private def verifyGovUKLink(): Unit = {
    Browser.clickButtonByIdOnceClickable("logo")
    Browser.checkHeader(GovUKPage)
    Browser.goBack()
  }

  When("^the user is not logged in$") {
  }

  Then("^they can access the cannot change email try later page$") {
    CannotChangeEmailPageTryLater.navigate()
    Browser.checkCurrentPageIs(CannotChangeEmailPageTryLater)
    CannotChangeEmailPageTryLater.checkForOldQuotes()
    Browser.checkForBadContent(CannotChangeEmailPageTryLater)
  }

  Then("^they access the sign in page$") {
    HTSSignInPage.navigate()
    Browser.checkCurrentPageIs(HTSSignInPage)
    HTSSignInPage.checkForOldQuotes()
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

  Then("^they see the cannot change email page$") {
    CannotChangeEmailPage.navigate()
    Browser.checkCurrentPageIs(CannotChangeEmailPage)
    CannotChangeEmailPage.checkForOldQuotes()
    Browser.checkForBadContent(CannotChangeEmailPage)
  }

  Then("^they see the cannot change email try later page$") {
    CannotChangeEmailPageTryLater.navigate()
    Browser.checkCurrentPageIs(CannotChangeEmailPageTryLater)
    Browser.checkForBadContent(CannotChangeEmailPageTryLater)
  }

  Then("^they navigate to and see the close account are you sure page$") {
    CloseAccountPage.navigate()
    Browser.checkCurrentPageIs(CloseAccountPage)
    CloseAccountPage.checkForOldQuotes()
    Browser.checkForBadContent(CloseAccountPage)
  }

  When("^an eligible applicant logs into gg$") {
    AuthorityWizardPage.authenticateEligibleUser(ApplyPage.expectedURL, ScenarioContext.generateEligibleNINO())
  }

  When("^an applicant logs into gg with missing details$") {
    AuthorityWizardPage.authenticateMissingDetailsUser(ApplyPage.expectedURL, ScenarioContext.generateEligibleNINO())
  }

  Then("^they see a page showing which details are missing$") {
    MissingInfoPage.navigate()
    Browser.checkCurrentPageIs(MissingInfoPage)
    MissingInfoPage.checkForOldQuotes()
    Browser.checkForBadContent(MissingInfoPage)
  }

  Then("^they see the your link has expired page$") {
    LinkExpiredPage.navigate()
    Browser.checkCurrentPageIs(LinkExpiredPage)
    LinkExpiredPage.checkForOldQuotes()
    Browser.checkForBadContent(LinkExpiredPage)
  }

  Then("^they see the create account error page$") {
    CreateAccountErrorPage.navigate()
    CreateAccountErrorPage.checkForOldQuotes()
    Browser.checkCurrentPageIs(CreateAccountErrorPage)
    Browser.checkForBadContent(CreateAccountErrorPage)
  }

}
