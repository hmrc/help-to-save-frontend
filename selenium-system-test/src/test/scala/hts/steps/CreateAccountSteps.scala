/*
 * Copyright 2017 HM Revenue & Customs
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

import hts.pages._
import hts.pages.registrationPages._
import hts.utils.{Configuration, ScenarioContext}
import hts.utils.EitherOps._
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig

class CreateAccountSteps extends Steps with Page {

  Given("""^A user is at the start of the registration process$""") { () ⇒
    AboutPage.navigate()
  }

  Given("""^An authenticated user is at the start of the registration process$""") { () ⇒
    AuthorityWizardPage.authenticateUser(AboutPage.url, 200, "Strong", ScenarioContext.generateEligibleNINO())
  }

  Given("""^a user is on the apply page$""") { () ⇒
    ApplyPage.navigate()
  }

  Given("""^an authenticated user is on the apply page$""") { () ⇒
    AuthorityWizardPage.authenticateUser(ApplyPage.url, 200, "Strong", ScenarioContext.generateEligibleNINO())
  }

  Given("""^a user has previously created an account$"""){ () ⇒
    AuthorityWizardPage.navigate()
    AuthorityWizardPage.authenticateUser(CheckEligibilityPage.url, 200, "Strong", ScenarioContext.generateEligibleNINO())
    EligiblePage.startCreatingAccount()
    ConfirmDetailsPage.continue()
    CreateAccountPage.createAccount()
    driver.manage().deleteAllCookies()
  }

  When("""^they proceed through to the apply page$""") { () ⇒
    AboutPage.nextPage()
    CheckEligibilityPage.nextPage()
    HowTheAccountWorksPage.nextPage()
    HowWeCalculateBonusesPage.nextPage()
  }

  When("""^they click on the Start now button$""") { () ⇒
    ApplyPage.clickStartNow()
  }

  When("""^they click on the sign in link$""") { () ⇒
    ApplyPage.clickSignInLink()
  }

  When("""^they choose to create an account$""") { () ⇒
    ConfirmDetailsPage.continue()
    CreateAccountPage.createAccount()
  }

  When("""^the user clicks on the check eligibility button$""") { () ⇒
    YouDoNotHaveAnAccountPage.clickCheckEligibility()
  }

  When("""^they have logged in again and passed IV$"""){ () ⇒
    driver.navigate().to(s"${Configuration.authHost}/auth-login-stub/gg-sign-in")
    AuthorityWizardPage.authenticateUser(CheckEligibilityPage.url, 200, "Strong", ScenarioContext.currentNINO())
  }

  Then("""^they see that the account is created$""") { () ⇒
    getCurrentUrl should include(FrontendAppConfig.nsiManageAccountUrl)
  }

  Then("""^they will be on a page which says you do not have an account$""") { () ⇒
    //    TODO: Uncomment when the placeholder page is replaced with the final page
    //    YouDoNotHaveAnAccountPage.pageInfoIsCorrectCorrect
  }

  Then("""^they will be on the you're eligible page$""") { () ⇒
    EligiblePage.pageInfoIsCorrect
  }

  Then("""^they will be on the account home page$"""){ () ⇒
    getCurrentUrl should include(FrontendAppConfig.nsiManageAccountUrl)
  }

  When("""^an applicant cancels their application just before giving the go-ahead to create an account$"""){ () ⇒
    AuthorityWizardPage.authenticateUser(CheckEligibilityPage.url, 200, "Strong", ScenarioContext.generateEligibleNINO())
    EligiblePage.startCreatingAccount()
    ConfirmDetailsPage.continue()
    CreateAccountPage.exitWithoutCreatingAccount()
  }

  Then("""^they see the Help to Save landing page \(with information about Help to Save\)$"""){ () ⇒
    AboutPage.pageInfoIsCorrect
  }

  When("""^they choose to go ahead with creating an account$"""){ () ⇒
    AuthorityWizardPage.enterUserDetails(200, "Strong", ScenarioContext.userInfo().getOrElse(sys.error))
    AuthorityWizardPage.setRedirect(EligiblePage.url)
    AuthorityWizardPage.submit()
    EligiblePage.startCreatingAccount()
    ConfirmDetailsPage.continue()
    CreateAccountPage.createAccount()
  }
}
