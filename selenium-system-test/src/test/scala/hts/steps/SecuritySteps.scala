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

import hts.pages.{AuthorityWizardPage, Page}
import hts.utils.Configuration

import scala.util.Random

class SecuritySteps extends Steps {

  def oneOfRegex(options: Set[String]): String = s"(${options.mkString("|")})"

  val confidenceLevelRegex: String = oneOfRegex(Set(50,100,200,300).map(_.toString))

  val credentialStrengthsRegex: String = oneOfRegex(Set("weak", "strong", "none"))

  Given(s"""^an applicant has a confidence level of $confidenceLevelRegex$$""") { (level: Int) =>
    AuthorityWizardPage.goToPage()
    AuthorityWizardPage.setAuthorityId(Random.nextString(5))
    AuthorityWizardPage.setRedirect(Configuration.host + "/help-to-save/register/declaration")
    AuthorityWizardPage.setConfidenceLevel(level)
  }

  Given(s"""^their credential strength is $credentialStrengthsRegex$$"""){ (strength : String) =>
    AuthorityWizardPage.setCredentialStrength(strength)
    AuthorityWizardPage.setNino("JA553215D")
    AuthorityWizardPage.submit()
  }

  Then("""^they are forced into going through 2SV before being able to proceed with their HtS application$"""){ () =>
    Page.getCurrentUrl() should include("one-time-password")
  }

  Given(s"""^an applicant's credential strength is $credentialStrengthsRegex$$"""){ (strength : String) =>
    AuthorityWizardPage.goToPage()
    AuthorityWizardPage.setAuthorityId(Random.nextString(5))
    AuthorityWizardPage.setCredentialStrength(strength)
    AuthorityWizardPage.setNino("JA553215D")
  }

  Given(s"""^their confidence level is $confidenceLevelRegex$$"""){ (level : Int) =>
    AuthorityWizardPage.setRedirect(Configuration.host + "/help-to-save/register/declaration")
    AuthorityWizardPage.setConfidenceLevel(level)
    AuthorityWizardPage.submit()
  }

  Then("""^they are forced into going through IV before being able to proceed with their HtS application$"""){ () =>
    Page.getCurrentUrl() should include("identity-verification")
  }

}
