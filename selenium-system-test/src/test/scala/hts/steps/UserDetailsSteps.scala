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

package src.test.scala.hts.steps

import cucumber.api.DataTable
import src.test.scala.hts.pages.{AuthorityWizardPage, Page}
import src.test.scala.hts.utils.{Configuration, NINOGenerator}

import scala.collection.JavaConverters._
import scala.collection.mutable

class UserDetailsSteps extends Steps with NINOGenerator{

  var name: Option[String] = None
  var nino: Option[String] = None
  var dateOfBirth: Option[String] = None
  var email: Option[String] = None

  Given("""^an applicant has the following details:$"""){ (applicantDetails:DataTable) =>
    val data: List[mutable.Map[String, String]] = applicantDetails.asMaps(classOf[String], classOf[String])
      .asScala
      .toList
      .map(_.asScala)

    data.foreach{ row =>
      row.get("field") -> row.get("value") match {
        case (Some(field), value @ Some(_)) =>
          field match {
            case "name"          => name = value
            case "NINO"          =>
            case "date of birth" => dateOfBirth = value
            case "email address" => email = value
            case other           => sys.error(s"Unexpected field: $other")
          }

        case _ => sys.error("Could not parse table row. Field or value not found")
      }
    }
  }

  When("""^an applicant passes the eligibility check$"""){ () =>
    AuthorityWizardPage.goToPage()
    AuthorityWizardPage.setRedirect(Configuration.host + "/help-to-save/register/check-eligibility")
    AuthorityWizardPage.setCredentialStrength("strong")
    AuthorityWizardPage.setConfidenceLevel(200)
    nino = Some(generateEligibleNINO)
    println("NINO: " + nino.getOrElse(sys.error("Could not find NINO")))
    AuthorityWizardPage.setNino(nino.getOrElse(sys.error("Could not find NINO")))
    AuthorityWizardPage.submit()
  }

  Then("""^they see their details$"""){ () =>
    Page.getPageContent() should include("Name: " + name.getOrElse(sys.error("Could not find name")))
    Page.getPageContent() should include("National Insurance number: " + nino.getOrElse(sys.error("Could not find NINO")))
    Page.getPageContent() should include("Date of Birth: " + dateOfBirth.getOrElse(sys.error("Could not find DoB")))
    Page.getPageContent() should include("Email: " + email.getOrElse(sys.error("Could not find email")))
  }

}
