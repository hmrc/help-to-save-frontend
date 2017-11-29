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

import hts.pages.registrationPages.EligibilityInfoPage
import hts.pages._
import hts.utils.{Configuration, Helpers, ScenarioContext}
import uk.gov.hmrc.helptosavefrontend.config.WSHttp

class SecuritySteps extends Steps with Page {

  def oneOfRegex(options: Set[String]): String = s"(${options.mkString("|")})"

  val confidenceLevelRegex: String = oneOfRegex(Set(50, 100, 200, 300).map(_.toString))

  val credentialStrengthsRegex: String = oneOfRegex(Set("weak", "strong", "none"))

  Given(s"""^a user has a confidence level of $confidenceLevelRegex$$""") { (level: Int) ⇒
    AuthorityWizardPage.navigate()
    AuthorityWizardPage.setRedirect(EligiblePage.url)
    AuthorityWizardPage.setConfidenceLevel(level)
  }

  Given(s"""^I have logged in to Government Gateway with a confidence level of $confidenceLevelRegex$$""") { (level: Int) ⇒
    AuthorityWizardPage.authenticateUser(EligiblePage.url, level, "Strong", ScenarioContext.generateEligibleNINO())
  }

  Then("""^I am forced into going through IV before being able to proceed with their HtS application$""") { () ⇒
    getCurrentUrl should include regex "/iv/journey-result|iv%2Fjourney-result"
  }

  Given("""^I have NOT logged in to Government Gateway$""") { () ⇒
    // Do nothing
  }

  Given("""^a user has logged in$""") { () ⇒
    AuthorityWizardPage.authenticateUser(EligiblePage.url, 200, "Strong", ScenarioContext.generateEligibleNINO())
  }

  When("""^they have logged in and passed IV$"""){ () ⇒
    AuthorityWizardPage.navigate()
    AuthorityWizardPage.authenticateUser(AccessAccountPage.url, 200, "Strong", ScenarioContext.generateEligibleNINO())
  }

  When("""^I try to view the user details page$""") { () ⇒
    EligiblePage.navigate()
  }

  When("""^I try to view the create-an-account page$""") { () ⇒
    CreateAccountPage.navigate()
  }

  Then("""^I am prompted to log in to Government Gateway$""") { () ⇒
    getCurrentUrl should include(s"${Configuration.ggHost}/gg/sign-in")
  }

  Given("""^a user has logged in and passed IV$""") { () ⇒
    AuthorityWizardPage.authenticateUser(EligiblePage.url, 200, "Strong", ScenarioContext.generateEligibleNINO())
  }

  Then("""^the GG sign in page is visible$"""){ () ⇒
    getCurrentUrl should include ("gg/sign-in?")
  }

  When("""^I call URI (.+) with HTTP method (.+)$"""){ (uri: String, httpMethod: String) ⇒
    val path = s"${Configuration.host}/help-to-save/$uri"
    navigate(path)
  }

  Then("""^I see a response$"""){ () ⇒
    getCurrentUrl should include ("")
  }

  Given("""^I have gone through GG/2SV/identity check but I am NOT eligible for Help to Save$"""){ () ⇒
    AuthorityWizardPage.authenticateUser(EligiblePage.url, 200, "Strong", ScenarioContext.generateIneligibleNINO())
  }

  Then("""^I still see confirmation that I am NOT eligible$"""){ () ⇒
    //Helpers.isTextOnPage("You're not eligible for a Help to Save account") shouldBe true
    getCurrentUrl should include(NotEligiblePage.expectedUrl)
  }

  Given("""^HMRC doesn't currently hold an email address for me$"""){ () ⇒
    AuthorityWizardPage.authenticateUserNoEmail(EligiblePage.url, 200, "Strong", ScenarioContext.generateEligibleNINO())
  }

}
