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

import java.text.SimpleDateFormat
import java.util.Date

import cucumber.api.DataTable
import hts.pages.{AuthorityWizardPage, EligiblePage, Page}
import hts.utils.{Configuration, NINOGenerator}

import scala.collection.JavaConverters._
import scala.collection.mutable

class UserDetailsSteps extends Steps with NINOGenerator {

  var firstName: Option[String] = None
  var lastName: Option[String] = None
  var nino: Option[String] = None
  var dateOfBirth: Option[Date] = None
  var email: Option[String] = None

  Given("""^an applicant has the following details:$"""){ (applicantDetails: DataTable) ⇒
    val data: List[mutable.Map[String, String]] = applicantDetails.asMaps(classOf[String], classOf[String])
      .asScala
      .toList
      .map(_.asScala)

    data.foreach{ row ⇒
      row.get("field") -> row.get("value") match {
        case (Some(field), value @ Some(_)) ⇒
          field match {
            case "first name" ⇒ firstName = value
            case "last name"  ⇒ lastName = value
            case "NINO"       ⇒ nino = Some(generateEligibleNINO)
            case "date of birth" ⇒ {
              val simpleDateFormat: SimpleDateFormat = new SimpleDateFormat("dd/mm/yyyy")
              dateOfBirth = Some(simpleDateFormat.parse(value.getOrElse(sys.error("Could not find date of birth"))))
            }
            case "email address" ⇒ email = value
            case other           ⇒ sys.error(s"Unexpected field: $other")
          }

        case _ ⇒ sys.error("Could not parse table row. Field or value not found")
      }
    }
  }

  When("""^an applicant passes the eligibility check$"""){ () ⇒
    AuthorityWizardPage.navigate()
    AuthorityWizardPage.setRedirect(EligiblePage.url)
    AuthorityWizardPage.setCredentialStrength("strong")
    AuthorityWizardPage.setConfidenceLevel(200)
    println("NINO: " + nino.getOrElse(sys.error("Could not find NINO")))
    AuthorityWizardPage.setNino(nino.getOrElse(sys.error("Could not find NINO")))
    AuthorityWizardPage.setGivenName(firstName.getOrElse(sys.error("Could not find first name")))
    AuthorityWizardPage.setFamilyName(lastName.getOrElse(sys.error("Could not find last name")))

    val date = new SimpleDateFormat("yyyy-mm-dd").format(dateOfBirth.getOrElse(sys.error("Could not find date of birth")))
    AuthorityWizardPage.setDateOfBirth(date)
    AuthorityWizardPage.setEmail(email.getOrElse(sys.error("Could not find email")))
    AuthorityWizardPage.setAddressLine1("Address line 1")
    AuthorityWizardPage.setAddressLine2("Address line 2")
    AuthorityWizardPage.setPostCode("BN2 3EF")
    AuthorityWizardPage.setCountryCode("01")
    AuthorityWizardPage.submit()
    EligiblePage.startCreatingAccount()
  }

  Then("""^they see their details$"""){ () ⇒
    Page.getPageContent should include(firstName.getOrElse(sys.error("Could not find first name")) + " " + lastName.getOrElse(sys.error("Could not find last name")))
    Page.getPageContent should include(nino.getOrElse(sys.error("Could not find NINO")))
    val date = new SimpleDateFormat("dd/mm/yyyy").format(dateOfBirth.getOrElse(sys.error("Could not find date of birth")))
    Page.getPageContent should include(date)
    Page.getPageContent should include(email.getOrElse(sys.error("Could not find email")))
  }
}
