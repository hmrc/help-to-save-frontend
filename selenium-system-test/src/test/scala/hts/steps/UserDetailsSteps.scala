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

import cucumber.api.DataTable
import hts.pages.{AuthorityWizardPage, Page}
import hts.utils.Configuration

import scala.collection.JavaConverters.mapAsScalaMapConverter

class UserDetailsSteps extends Steps{

  var name: Option[String] = None
  var nino: Option[String] = None
  var dateOfBirth: Option[String] = None
  var email: Option[String] = None

  Given("""^an applicant has the following details:$"""){ (applicantDetails:DataTable) =>
    val expectedData = applicantDetails.asMaps(classOf[String], classOf[String])

    for (i <- 0 to applicantDetails.getGherkinRows.size() - 2) {
      val row = expectedData.get(i).asScala.seq
      val field = Option(row("field")).getOrElse("")
      val value = Option(row("value"))

      field match {
        case "name" => name = value
        case "NINO" => nino = value
        case "date of birth" => dateOfBirth = value
        case "email address" => email = value
      }
    }
  }

  When("""^an applicant passes the eligibility check$"""){ () =>
    AuthorityWizardPage.goToPage()
    AuthorityWizardPage.setRedirect(Configuration.host + "/help-to-save/register/user-details")
    AuthorityWizardPage.setCredentialStrength("strong")
    AuthorityWizardPage.setConfidenceLevel(200)
    AuthorityWizardPage.setNino(nino.getOrElse(""))
    AuthorityWizardPage.submit()
  }

  Then("""^they see their details$"""){ () =>
    Page.getPageContent() should include("Name: " + name.getOrElse(""))
    Page.getPageContent() should include("National Insurance number: " + nino.getOrElse(""))
    Page.getPageContent() should include("Date of Birth: " + dateOfBirth.getOrElse(""))
    Page.getPageContent() should include("Email: " + email.getOrElse(""))
  }

}
