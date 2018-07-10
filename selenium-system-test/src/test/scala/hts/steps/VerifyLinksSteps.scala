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
import hts.steps.CommonSteps._

class VerifyLinksSteps extends Steps {

  Then("^they go through the happy path they can see and access all header and footer links$") {
    AboutPage.navigate()
    Browser.checkHeader(AboutPage)
    checkForLinksThatExistOnEveryPage(AboutPage)
    Browser.nextPage()

    Browser.checkHeader(EligibilityInfoPage)
    checkForLinksThatExistOnEveryPage(EligibilityInfoPage)
    Browser.nextPage()

    Browser.checkHeader(HowTheAccountWorksPage)
    checkForLinksThatExistOnEveryPage(HowTheAccountWorksPage)
    Browser.nextPage()

    Browser.checkHeader(HowWeCalculateBonusesPage)
    checkForLinksThatExistOnEveryPage(HowWeCalculateBonusesPage)
    verifyGovUKLink()
    Browser.nextPage()

    Browser.checkHeader(ApplyPage)
    checkForLinksThatExistOnEveryPage(ApplyPage)
    ApplyPage.clickStartNow()

    Browser.checkHeader(EligiblePage)
    checkForLinksThatExistOnEveryPage(EligiblePage)

    EligiblePage.clickConfirmAndContinue()
    checkForLinksThatExistOnEveryPage(SelectEmailPage)

    //try to change the email and verify links
    SelectEmailPage.setAndVerifyNewEmail("newemail@mail.com")
    Browser.checkHeader(VerifyYourEmailPage)
    checkForLinksThatExistOnEveryPage(VerifyYourEmailPage)

    //go back to original select email page and continue
    Browser.goBack()
    SelectEmailPage.selectGGEmail()
    checkForLinksThatExistOnEveryPage(CreateAccountPage)
    CreateAccountPage.createAccount()

    Browser.checkHeader(NsiManageAccountPage)

    ChangeEmailPage.navigate()
    Browser.checkHeader(ChangeEmailPage)
    checkForLinksThatExistOnEveryPage(ChangeEmailPage)
    ChangeEmailPage.setNewEmailAddress("anotheremail@mail.com")

    Browser.checkHeader(VerifyEmailPage)
    checkForLinksThatExistOnEveryPage(VerifyEmailPage)
    VerifyEmailPage.resendEmail

    Browser.checkHeader(VerifyEmailPage)
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
  }

  Then("^they access the sign in page$") {
    HTSSignInPage.navigate()
    Browser.checkCurrentPageIs(HTSSignInPage)
  }

  Then("^they see the daily cap reached page$") {
    DailyCapReachedPage.navigate()
    Browser.checkCurrentPageIs(DailyCapReachedPage)
  }

  Then("^they see the total cap reached page$") {
    TotalCapReachedPage.navigate()
    Browser.checkCurrentPageIs(TotalCapReachedPage)
  }

  Then("^they see the service unavailable page$") {
    ServiceUnavailablePage.navigate()
    Browser.checkCurrentPageIs(ServiceUnavailablePage)
  }

  Then("^they see the cannot change email page$") {
    CannotChangeEmailPage.navigate()
    Browser.checkCurrentPageIs(CannotChangeEmailPage)
  }

  Then("^they see the cannot change email try later page$") {
    CannotChangeEmailPageTryLater.navigate()
    Browser.checkCurrentPageIs(CannotChangeEmailPageTryLater)
  }

  Then("^they navigate to and see the close account are you sure page$") {
    CloseAccountPage.navigate()
    Browser.checkCurrentPageIs(CloseAccountPage)
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
  }

  Then("^they see the your link has expired page$") {
    LinkExpiredPage.navigate()
    Browser.checkCurrentPageIs(LinkExpiredPage)
  }

  Then("^they see the create account error page$") {
    CreateAccountErrorPage.navigate()
    Browser.checkCurrentPageIs(CreateAccountErrorPage)
  }

}
