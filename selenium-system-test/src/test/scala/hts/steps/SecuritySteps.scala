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

import hts.pages.registrationPages.CheckEligibilityPage
import hts.pages._
import hts.utils.{Configuration, ScenarioContext}
import uk.gov.hmrc.helptosavefrontend.config.WSHttp

class SecuritySteps extends Steps {

  def oneOfRegex(options: Set[String]): String = s"(${options.mkString("|")})"

  val confidenceLevelRegex: String = oneOfRegex(Set(50, 100, 200, 300).map(_.toString))

  val credentialStrengthsRegex: String = oneOfRegex(Set("weak", "strong", "none"))

  Given(s"""^a user has a confidence level of $confidenceLevelRegex$$""") { (level: Int) ⇒
    AuthorityWizardPage.navigate()
    AuthorityWizardPage.setRedirect(CheckEligibilityPage.url)
    AuthorityWizardPage.setConfidenceLevel(level)
  }

  Given(s"""^a user has logged in to Government Gateway with a confidence level of $confidenceLevelRegex$$""") { (level: Int) ⇒
    AuthorityWizardPage.authenticateUser(CheckEligibilityPage.url, level, "Strong", ScenarioContext.generateEligibleNINO())
  }

  Then("""^they are forced into going through IV before being able to proceed with their HtS application$""") { () ⇒
    Page.getCurrentUrl should include regex "/iv/journey-result|iv%2Fjourney-result"
  }

  Given("""^a user has NOT logged in to Government Gateway$""") { () ⇒
    // Do nothing
  }

  Given("""^a user has logged in$""") { () ⇒
    AuthorityWizardPage.authenticateUser(CheckEligibilityPage.url, 200, "Strong", ScenarioContext.generateEligibleNINO())
  }

  When("""^they have logged in and passed IV$"""){ () ⇒
    AuthorityWizardPage.navigate()
    AuthorityWizardPage.authenticateUser(AccessAccountPage.url, 200, "Strong", ScenarioContext.generateEligibleNINO())
  }

  When("""^they try to view the user details page$""") { () ⇒
    ConfirmDetailsPage.navigate()
  }

  When("""^they try to view the create-an-account page$""") { () ⇒
    CreateAccountPage.navigate()
  }

  Then("""^they are prompted to log in to Government Gateway$""") { () ⇒
    Page.getCurrentUrl should include("gg/sign-in")
  }

  Given("""^a user has logged in and passed IV$""") { () ⇒
    AuthorityWizardPage.authenticateUser(CheckEligibilityPage.url, 200, "Strong", ScenarioContext.generateEligibleNINO())
  }

  Then("""^the GG sign in page is visible$"""){ () ⇒
    driver.getCurrentUrl should include ("gg/sign-in?")
  }

  When("""^I call URI (.+) with HTTP method (.+)$"""){ (uri: String, httpMethod: String) ⇒
    //http.httpMethod(s"${Configuration.host}/help-to-save/uri")
    val path = s"${Configuration.host}/help-to-save/uri"
    //Page.hitPage(httpMethod, path)
    Page.navigate(path)
  }

  Then("""^I see a response$"""){ () ⇒
    driver.getCurrentUrl should include ("")
  }

}
