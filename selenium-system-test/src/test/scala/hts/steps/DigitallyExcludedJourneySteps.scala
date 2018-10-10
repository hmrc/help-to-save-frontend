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
import hts.pages.{AccountCreatedPage, CreateAccountPage, IntroductionHelpToSavePage}
import hts.pages.eligibility.CustomerEligiblePage
import stride.pages.StrideSignInPage

class DigitallyExcludedJourneySteps extends Steps {

  Given("^the operator is logged in$") {
    StrideSignInPage.authenticateOperator()
  }

  When("^the internal operator chooses to create an account on behalf of the applicant$") {
    CustomerEligiblePage.continue()
    CreateAccountPage.createAccount()
  }

  Then("^an account is successfully created$") {
    Browser.checkCurrentPageIs(AccountCreatedPage)
  }

  When("^the internal operator is in the process of creating an account on behalf of the applicant$") {
    CustomerEligiblePage.continue()
  }

  Then("^they have the option to enter a new applicant's NINO on the opening screen$") {
    Browser.checkCurrentPageIs(IntroductionHelpToSavePage)
  }

  When("^the internal operator attempts to create an account on behalf of the applicant$") {
    CustomerEligiblePage.continue()
    CreateAccountPage.createAccount()
  }
}
