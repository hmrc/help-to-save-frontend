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

import hts.pages.{AuthorityWizardPage, ConfirmDetailsPage, CreateAccountPage, Page}
import hts.utils.{Configuration, NINOGenerator}

class SecuritySteps extends Steps with NINOGenerator {

  def oneOfRegex(options: Set[String]): String = s"(${options.mkString("|")})"

  val confidenceLevelRegex: String = oneOfRegex(Set(50, 100, 200, 300).map(_.toString))

  val credentialStrengthsRegex: String = oneOfRegex(Set("weak", "strong", "none"))

  Given(s"""^a user has a confidence level of $confidenceLevelRegex$$""") { (level: Int) =>
    AuthorityWizardPage.goToPage()
    AuthorityWizardPage.setRedirect(Configuration.host + "/help-to-save/register/confirm-details")
    AuthorityWizardPage.setConfidenceLevel(level)
  }

  Given(s"""^their confidence level is $confidenceLevelRegex$$""") { (level: Int) =>
    AuthorityWizardPage.setConfidenceLevel(level)
    AuthorityWizardPage.submit()
  }

  Then("""^they are forced into going through IV before being able to proceed with their HtS application$""") { () =>
    Page.getCurrentUrl() should include regex ("/iv/journey-result|iv%2Fjourney-result")
  }

  Given("""^a user has NOT logged in$""") { () =>
    // Do nothing
  }

  Given("""^a user has logged in$""") { () =>
    AuthorityWizardPage.authenticateUser(s"${Configuration.host}/help-to-save/check-eligibility", 200, "Strong", generateEligibleNINO)
  }

  When("""^they have logged in and passed IV$"""){ () =>
    AuthorityWizardPage.authenticateUser(s"${Configuration.host}/help-to-save/access-account", 200, "Strong", generateEligibleNINO)
  }

  When("""^they have logged in and passed IV2$"""){ () =>
    AuthorityWizardPage.authenticateUser(s"${Configuration.host}/help-to-save/access-account", 200, "Strong", AuthorityWizardPage.getNino)
  }

  When("""^they try to view the user details page$""") { () =>
    ConfirmDetailsPage.goToPage()
  }

  When("""^they try to view the create-an-account page$""") { () =>
    CreateAccountPage.goToPage()
  }

  Then("""^they are prompted to log in$""") { () =>
    Page.getCurrentUrl() should include("gg/sign-in")
  }

  Given("""^a user has logged in and passed IV$""") { () =>
    AuthorityWizardPage.authenticateUser(s"${Configuration.host}/help-to-save/check-eligibility", 200, "Strong", generateEligibleNINO)
  }

  Then("""^the GG sign in page is visible$"""){ () =>
    driver.getCurrentUrl should include ("gg/sign-in?")
  }

}
