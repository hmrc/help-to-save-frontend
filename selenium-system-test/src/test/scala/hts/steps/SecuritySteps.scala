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

import cucumber.api.java.en.Given
import hts.pages.{AuthorityWizardPage, CreateAccountPage, Page, ConfirmDetailsPage}
import hts.utils.Configuration
import uk.gov.hmrc.domain.Generator

class SecuritySteps extends Steps {

  def oneOfRegex(options: Set[String]): String = s"(${options.mkString("|")})"

  val confidenceLevelRegex: String = oneOfRegex(Set(50, 100, 200, 300).map(_.toString))

  val credentialStrengthsRegex: String = oneOfRegex(Set("weak", "strong", "none"))

  val generator = new Generator()
  val nino = generator.nextNino.toString()

  Given(s"""^an applicant has a confidence level of $confidenceLevelRegex$$""") { (level: Int) =>
    AuthorityWizardPage.goToPage()
    AuthorityWizardPage.setRedirect(Configuration.host + "/help-to-save/register/confirm-details")
    println(Configuration.host + "/help-to-save/register/confirm-details")
    AuthorityWizardPage.setConfidenceLevel(level)
  }

  Given(s"""^their confidence level is $confidenceLevelRegex$$""") { (level: Int) =>
    AuthorityWizardPage.setConfidenceLevel(level)
    AuthorityWizardPage.submit()
  }

  Then("""^they are forced into going through IV before being able to proceed with their HtS application$""") { () =>
    Page.getCurrentUrl() should include regex ("/iv/journey-result|iv%2Fjourney-result")
  }

  Given("""^an applicant has NOT logged in$""") { () =>
    // Do nothing
  }

  Given("""^an applicant has logged in$""") { () =>
    AuthorityWizardPage.goToPage()
    AuthorityWizardPage.setCredentialStrength("strong")
    AuthorityWizardPage.setNino(nino)
    AuthorityWizardPage.setRedirect(Configuration.host + "/help-to-save/register/confirm-details")
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

  Given("""^an applicant has logged in and passed IV$""") { () =>
    AuthorityWizardPage.goToPage()
    AuthorityWizardPage.setRedirect(Configuration.host + "/help-to-save/register/confirm-details")
    AuthorityWizardPage.setCredentialStrength("strong")
    AuthorityWizardPage.setConfidenceLevel(200)
    AuthorityWizardPage.setNino(nino)
    AuthorityWizardPage.submit()
  }

}
