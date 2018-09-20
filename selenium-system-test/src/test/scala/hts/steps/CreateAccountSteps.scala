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
import hts.utils.EitherOps._
import hts.utils.{ScenarioContext, TestBankDetails}

class CreateAccountSteps extends Steps {
  When("^they try to sign in without being logged in GG$") {
    Browser.navigateTo("access-account")
  }

  Given("^the authenticated user tries to sign in$|^they log in$") {
    AuthorityWizardPage.authenticateEligibleUser(AccessAccountLink.expectedURL, ScenarioContext.generateEligibleNINO())
  }

  Given("^they try to start creating an account$") {
    Browser.navigateTo("check-eligibility")
  }

  Given("^a user has previously created an account$") {
    AuthorityWizardPage.authenticateEligibleUser(EligiblePage.expectedURL, ScenarioContext.generateEligibleNINO())
    createAccountUsingGGEmail()
  }

  When("^they log in and proceed to create an account using their GG email$") {
    AuthorityWizardPage.authenticateEligibleUser(EligiblePage.expectedURL, ScenarioContext.generateEligibleNINO())
    createAccountUsingGGEmail()
  }

  When("^they choose to go ahead with creating an account$") {
    AuthorityWizardPage.enterUserDetails(200, "strong", ScenarioContext.userInfo().getOrElse(sys.error))
    createAccountUsingGGEmail()
  }

  When("^they see their details are incorrect and report it$") {
    EligiblePage.continue()

    SelectEmailPage.setAndVerifyNewEmail("newemail@mail.com")
    Browser.checkCurrentPageIs(VerifyYourEmailPage)

    Browser.goBack()
    SelectEmailPage.selectGGEmail()

    Browser.checkCurrentPageIs(BankDetailsPage)
    BankDetailsPage.enterDetails(TestBankDetails.ValidBankDetails)

    Browser.checkCurrentPageIs(CheckDetailsCreateAccountPage)
    CheckDetailsCreateAccountPage.detailsNotCorrect()

    Browser.checkCurrentPageIs(IncorrectDetailsPage)
    IncorrectDetailsPage.checkForOldQuotes()
    Browser.checkForLinksThatExistOnEveryPage(IncorrectDetailsPage)
    IncorrectDetailsPage.clickBack

    CheckDetailsCreateAccountPage.detailsNotCorrect()

    Browser.checkHeader(IncorrectDetailsPage)
  }

  Then("^they see the HMRC change of details page$") {
    Browser.clickLinkTextOnceClickable("Tell HMRC about a change to your personal details")
    Browser.checkExternalPageIs(HMRCChangeOfDetailsPage)
  }

  When("^they proceed to create an account using their GG email$") {
    EligiblePage.navigate()
    createAccountUsingGGEmail()
  }

  When("^they proceed to create an account$"){
    EligiblePage.navigate()
    createAccountError()
  }

  When("^they click on accept and create an account$") {
    CheckDetailsCreateAccountPage.createAccount()
    Browser.checkCurrentPageIs(NsiManageAccountPage)
  }

  When("^the user continues$") {
    YouDoNotHaveAnAccountPage.clickContinue()
    Browser.checkHeader(EligiblePage)
  }

  When("^they log in again$") {
    AuthorityWizardPage.authenticateEligibleUser(AccessAccountLink.expectedURL, ScenarioContext.currentNINO())
  }

  Then("^they are informed they don't have an account$") {
    Browser.checkCurrentPageIs(YouDoNotHaveAnAccountPage)
    Browser.checkForLinksThatExistOnEveryPage(YouDoNotHaveAnAccountPage)
  }

  Then("^they see that the account is created$|^they will be on the account home page$") {
    Browser.checkHeader(AccountCreatedPage)
  }

}
