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
import hts.pages.registrationPages.CheckEligibilityPage
import hts.utils.NINOGenerator

class EmailSteps extends Steps with NINOGenerator {

  Given("""^I am viewing my applicant details$"""){ () ⇒
    AuthorityWizardPage.authenticateUser(CheckEligibilityPage.url, 200, "Strong", currentEligibleNINO)
    EligiblePage.startCreatingAccount()
  }

  When("""^I choose to change my email address$"""){ () ⇒
    ConfirmDetailsPage.changeEmail()
    ChangeEmailPage.setAndVerifyNewEmail("newemail@mail.com")
  }

  Then("""^I am asked to check my email account for a verification email$"""){ () ⇒
    Page.getPageContent contains "Check your email"
  }

  Given("""^I've chosen to change my email address from A to B during the application process$"""){ () ⇒
    AuthorityWizardPage.authenticateUser(CheckEligibilityPage.url, 200, "Strong", currentEligibleNINO)
    EligiblePage.startCreatingAccount()
    ConfirmDetailsPage.changeEmail()
    ChangeEmailPage.setAndVerifyNewEmail("newemail@mail.com")
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
    ChangeEmailPage.setAndVerifyNewEmail("secondnewemail@mail.com")
  }

  When("""^I verify email address C$"""){ () ⇒

  }

  Then("""^I see that my saved email address is C$"""){ () ⇒

  }

}
