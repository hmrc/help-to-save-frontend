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
import hts.utils.ScenarioContext

class EmailSteps extends Steps {

  Given("""^I am viewing my applicant details$"""){ () ⇒
    AuthorityWizardPage.authenticateUser(EligiblePage.expectedURL, 200, "Strong", ScenarioContext.currentNINO())
    EligiblePage.startCreatingAccount()
  }

  When("""^they are shown a page to select which email address to use for hts$"""){ () ⇒
    Browser.checkCurrentPageIs(SelectEmailPage)
  }

  When("""^they select the email obtained from GG and click Continue$"""){ () ⇒
    Browser.checkCurrentPageIs(SelectEmailPage)
    SelectEmailPage.selectGGEmail()
    SelectEmailPage.clickContinue()
  }

  Then("""^I am asked to check my email account for a verification email$"""){ () ⇒
    Browser.checkCurrentPageIs(CheckYourEmailPage)
  }

  Given("""^I've chosen to change my email address from A to B during the application process$"""){ () ⇒
    AuthorityWizardPage.authenticateUser(EligiblePage.expectedURL, 200, "Strong", ScenarioContext.currentNINO())
    EligiblePage.startCreatingAccount()
    SelectEmailPage.setAndVerifyNewEmail("newemail@mail.com")
  }

  Given("""^I want to receive a second verification email$"""){ () ⇒
    //Do nothing
  }

  When("""^I request a re-send of the verification email$"""){ () ⇒
    CheckYourEmailPage.resendVerificationEmail()
  }

  When("""^I verify email address B using the second verification email$"""){ () ⇒

  }

  Then("""^I see that my saved email address is B$"""){ () ⇒

  }

  Given("""^I haven't yet verified new email address B$"""){ () ⇒
    //Do nothing
  }

  When("""^I then choose to change the email address from B to C$"""){ () ⇒
    CheckYourEmailPage.changeEmail()
    SelectEmailPage.setAndVerifyNewEmail("secondnewemail@mail.com")
  }

  When("""^I verify email address C$"""){ () ⇒

  }

  Then("""^I see that my saved email address is C$"""){ () ⇒

  }

  Then("""^I am shown a page to enter my email address$""") { () ⇒
    Browser.checkCurrentPageIs(GiveEmailPage)
  }

  Then("""^they see the page "You're about to create a Help to Save account"$""") { () ⇒
    Browser.checkCurrentPageIs(CreateAccountPage)
  }

  When("""^they select I want to enter a new email address and enter a new email$"""){ () ⇒
    Browser.checkCurrentPageIs(SelectEmailPage)
    SelectEmailPage.selectNewEmail()
    SelectEmailPage.setNewEmail("newemail@gmail.com")
    SelectEmailPage.clickContinue()
  }

  Then("""^I see the page "You have 30 minutes to verify your email address"$""") { () ⇒
    Browser.checkCurrentPageIs(CheckYourEmailPage)
  }
}
