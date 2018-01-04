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
import hts.utils.{Configuration, ScenarioContext}

class CreateAccountSteps extends Steps {

  private def createAccountUsingGGEmail(): Unit = {
    EligiblePage.clickConfirmAndContinue()
    SelectEmailPage.selectGGEmail()
    CreateAccountPage.createAccount()
  }

  Given("^an applicant is on the home page$") { () ⇒
    AboutPage.navigate()
  }

  When("^they try to sign in through the Apply page without being logged in GG$") { () ⇒
    driver.manage().deleteAllCookies()
    ApplyPage.navigate()
    ApplyPage.clickSignInLink()
  }

  Given("^the authenticated user tries to sign in through the Apply page$|^they log in$") { () ⇒
    AuthorityWizardPage.authenticateEligibleUser(ApplyPage.expectedURL, ScenarioContext.generateEligibleNINO())
    ApplyPage.clickSignInLink()
  }

  Given("^they try to start creating an account from the Apply page$") {
    ApplyPage.navigate()
    ApplyPage.clickStartNow()
  }

  Given("^a user has previously created an account$") { () ⇒
    AuthorityWizardPage.authenticateEligibleUser(EligiblePage.expectedURL, ScenarioContext.generateEligibleNINO())
    createAccountUsingGGEmail()
  }

  When("^they choose to go ahead with creating an account$|^they log in and proceed to create an account using their GG email$") { () ⇒
    AuthorityWizardPage.authenticateEligibleUser(EligiblePage.expectedURL, ScenarioContext.generateEligibleNINO())
    createAccountUsingGGEmail()
  }

  When("^an applicant cancels their application just before creating an account$") { () ⇒
    AuthorityWizardPage.authenticateEligibleUser(EligiblePage.expectedURL, ScenarioContext.generateEligibleNINO())
    EligiblePage.clickConfirmAndContinue()
    SelectEmailPage.selectGGEmail()
    CreateAccountPage.exitWithoutCreatingAccount()
  }

  When("^they proceed to create an account using their GG email$"){
    EligiblePage.navigate()
    createAccountUsingGGEmail()
  }

  When("^they proceed to the Apply page and click on the Start now button$") { () ⇒
    Browser.checkCurrentPageIs(AboutPage)
    Browser.nextPage()

    Browser.checkCurrentPageIs(EligibilityInfoPage)
    Browser.nextPage()

    Browser.checkCurrentPageIs(HowTheAccountWorksPage)
    Browser.nextPage()

    Browser.checkCurrentPageIs(HowWeCalculateBonusesPage)
    Browser.nextPage()

    Browser.checkCurrentPageIs(ApplyPage)
    ApplyPage.clickStartNow()
  }

  When("^they click on accept and create an account$") { () ⇒
    CreateAccountPage.createAccount()
    Browser.checkCurrentPageIs(NsiManageAccountPage)
  }

  When("^the user continues$") { () ⇒
    YouDoNotHaveAnAccountPage.clickContinue()
    Browser.checkCurrentPageIs(EligiblePage)
  }

  When("^they log in again$") { () ⇒
    AuthorityWizardPage.authenticateEligibleUser(ApplyPage.expectedURL, ScenarioContext.currentNINO())
    ApplyPage.clickSignInLink()
  }

  Then("^they are informed they don't have an account$") { () ⇒
    Browser.checkCurrentPageIs(YouDoNotHaveAnAccountPage)
  }

  Then("^they see the Help to Save About page$") { () ⇒
    Browser.checkCurrentPageIs(AboutPage)
  }

  Then("^they see that the account is created$|^they will be on the account home page$") { () ⇒
    Browser.checkCurrentPageIs(NsiManageAccountPage)
  }

}
