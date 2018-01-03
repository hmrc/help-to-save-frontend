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

  Given("^an applicant is on the home page$") { () ⇒
    AboutPage.navigate()
  }

  When("^the user tries to sign in through the Apply page$") { () ⇒
    ApplyPage.navigate()
    ApplyPage.clickSignInLink()
  }

  Given("^an authenticated user tries to sign in through the Apply page$") { () ⇒
    AuthorityWizardPage.authenticateUser(ApplyPage.expectedURL, 200, "Strong", ScenarioContext.generateEligibleNINO())
    ApplyPage.clickSignInLink()
  }

  Given("^a user has previously created an account$") { () ⇒
    AuthorityWizardPage.authenticateUser(EligiblePage.expectedURL, 200, "Strong", ScenarioContext.generateEligibleNINO())
    EligiblePage.clickConfirmAndContinue()
    SelectEmailPage.selectGGEmail()
    CreateAccountPage.createAccount()
    driver.manage().deleteAllCookies()
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

  When("^they have logged in again and passed IV$") { () ⇒
    driver.navigate().to(s"${Configuration.authHost}/auth-login-stub/gg-sign-in")
    AuthorityWizardPage.authenticateUser(ApplyPage.expectedURL, 200, "Strong", ScenarioContext.currentNINO())
    ApplyPage.clickSignInLink()
  }

  When("^an applicant cancels their application just before creating an account$") { () ⇒
    AuthorityWizardPage.authenticateUser(EligiblePage.expectedURL, 200, "Strong", ScenarioContext.generateEligibleNINO())
    EligiblePage.clickConfirmAndContinue()
    SelectEmailPage.selectGGEmail()
    CreateAccountPage.exitWithoutCreatingAccount()
  }

  When("^they choose to go ahead with creating an account$") { () ⇒
    AuthorityWizardPage.enterUserDetails(200, "Strong", ScenarioContext.userInfo().getOrElse(sys.error))
    AuthorityWizardPage.setRedirect(EligiblePage.expectedURL)
    AuthorityWizardPage.submit()
    EligiblePage.clickConfirmAndContinue()
    SelectEmailPage.selectGGEmail()
    CreateAccountPage.createAccount()
  }

  Then("^they see a page stating they don't have an account$") { () ⇒
    Browser.checkCurrentPageIs(YouDoNotHaveAnAccountPage)
  }

  Then("^they see the Help to Save About page$") { () ⇒
    Browser.checkCurrentPageIs(AboutPage)
  }

  Then("^they will be on the account home page$") { () ⇒
    Browser.checkCurrentPageIs(NsiManageAccountPage)
  }
}
