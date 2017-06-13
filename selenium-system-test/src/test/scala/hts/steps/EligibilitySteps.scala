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
import hts.utils.Configuration
import uk.gov.hmrc.domain.Generator

class EligibilitySteps extends Steps{

  var nino: Option[String] = None

  def generateEligibleNINO: String = {
    val generator = new Generator()
    val nino = generator.nextNino.toString()
    "AE" + nino.slice(2, nino.length)
  }

  def generateIllegibleNINO: String = {
    val generator = new Generator()
    val nino = generator.nextNino.toString()
    "NA" + nino.slice(2, nino.length)
  }

  Given("""^an applicant is in receipt of working tax credit$""") { () =>
    nino = Option(generateEligibleNINO)
  }

  When("""^they apply for Help to Save$""") { () =>
    AuthorityWizardPage.goToPage()
    AuthorityWizardPage.setRedirect(Configuration.host + "/help-to-save/register/confirm-details")
    AuthorityWizardPage.setCredentialStrength("strong")
    AuthorityWizardPage.setConfidenceLevel(200)
    AuthorityWizardPage.setNino(nino.getOrElse(""))
    AuthorityWizardPage.submit()
  }

  Then("""^they see that they are eligible for Help to Save$""") { () =>
    Page.getPageContent() should include("Check and confirms your details")
  }

  Given("""^an applicant is NOT in receipt of working tax credit$""") { () =>
    nino = Option(generateIllegibleNINO)
  }

  Then("""^they see that they are NOT eligible for Help to Save$""") { () =>
    Page.getPageContent() should include("You're not eligible")
  }

}
