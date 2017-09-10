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
import hts.utils.{Configuration, NINOGenerator}

class CreateAccountSteps extends Steps with NINOGenerator {

  Given("""^A user is at the start of the registration process$""") { () ⇒
    AboutPage.navigateToAboutPage
  }

  Given("""^An authenticated user is at the start of the registration process$""") { () ⇒
    AuthorityWizardPage.authenticateUser(s"${Configuration.host}/help-to-save/apply-for-help-to-save/about-help-to-save", 200, "Strong", generateEligibleNINO)
  }

  Given("""^a user is on the apply page$""") { () ⇒
    ApplyPage.navigateToApplyPage
  }

  Given("""^an authenticated user is on the apply page$""") { () ⇒
    AuthorityWizardPage.authenticateUser(s"${Configuration.host}/help-to-save/apply-for-help-to-save/apply", 200, "Strong", generateEligibleNINO)
  }

  Given("""^a user has previously created an account$"""){ () ⇒
    AuthorityWizardPage.goToPage()
    AuthorityWizardPage.authenticateUser(s"${Configuration.host}/help-to-save/check-eligibility", 200, "Strong", generateEligibleNINO)
    EligiblePage.startCreatingAccount()
    ConfirmDetailsPage.continue()
    CreateAccountPage.createAccount()
    driver.manage().deleteAllCookies()
  }

  When("""^they proceed through to the apply page$""") { () ⇒
    AboutPage.nextPage()
    EligibilityPage.nextPage()
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
    EligibilityQuestionPage.clickCheckEligibility()
  }

  When("""^they have logged in again and passed IV$"""){ () ⇒
    driver.navigate().to(s"${Configuration.authHost}/auth-login-stub/gg-sign-in")
    AuthorityWizardPage.authenticateUser(s"${Configuration.host}/help-to-save/check-eligibility", 200, "Strong", currentEligibleNINO)
  }

  Then("""^they see that the account is created$""") { () ⇒
    Page.getPageContent should include("Successfully created account")
  }

  Then("""^they will be on the eligibility question page$""") { () ⇒
    on(EligibilityQuestionPage)
  }

  Then("""^they will be on the you're eligible page$""") { () ⇒
    on(EligiblePage)
  }

  Then("""^they will be on the account home page$"""){ () ⇒
    Page.getPageContent contains "You've already got an account - yay!"
  }

  When("""^an applicant cancels their application just before giving the go-ahead to create an account$"""){ () ⇒
    AuthorityWizardPage.authenticateUser(s"${Configuration.host}/help-to-save/check-eligibility", 200, "Strong", generateEligibleNINO)
    EligiblePage.startCreatingAccount()
    ConfirmDetailsPage.continue()
    CreateAccountPage.exitWithoutCreatingAccount()
  }

  Then("""^they see the Help to Save landing page \(with information about Help to Save\)$"""){ () ⇒
    on(AboutPage)
  }
}
