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

import cats.syntax.either._
import hts.pages.{AuthorityWizardPage, Page}

class SecuritySteps extends Steps {


  Given("^an applicant has a confidence level of (.*)$") { (level: Int) =>
    AuthorityWizardPage.goToPage()
    AuthorityWizardPage.credId()
    AuthorityWizardPage.redirect("https://www-dev.tax.service.gov.uk/help-to-save/register/declaration")
    AuthorityWizardPage.confidenceLevel(level)
  }

  Given("""^their credential strength is (.*)$"""){ (strength : String) =>
    AuthorityWizardPage.credentialStrength(strength)
    AuthorityWizardPage.nino("JA553215D")
    AuthorityWizardPage.submit
    Thread.sleep(1000L)
  }

  Then("""^they are forced into going through 2SV before being able to proceed with their HtS application$"""){ () =>
    Page.getCurrentUrl() should include("one-time-password")
  }

  Given("""^an applicant's credential strength is (.*)$"""){ (strength : String) =>
    AuthorityWizardPage.goToPage()
    AuthorityWizardPage.credId()
    AuthorityWizardPage.credentialStrength(strength)
    AuthorityWizardPage.nino("JA553215D")
  }

  Given("""^their confidence level is (.*)$"""){ (level : Int) =>
    AuthorityWizardPage.redirect("https://www-dev.tax.service.gov.uk/help-to-save/register/declaration")
    AuthorityWizardPage.confidenceLevel(level)
    AuthorityWizardPage.submit
  }

  Then("""^they are forced into going through IV before being able to proceed with their HtS application$"""){ () =>
    Page.getCurrentUrl() should include("identity-verification")
  }

  Before { _ ⇒
    driver = newDriver()
  }

  After { _ ⇒
    driver.foreach(_.quit())
    Thread.sleep(2000L)
  }

}
