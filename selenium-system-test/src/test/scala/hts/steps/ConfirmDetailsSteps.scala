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

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import cucumber.api.DataTable
import hts.pages.{AuthorityWizardPage, EligiblePage, Page}
import hts.utils.{NINOGenerator, ScenarioContext}
import uk.gov.hmrc.helptosavefrontend.models.{Address, UserInfo}

import scala.collection.JavaConverters._
import hts.utils.Helpers

class ConfirmDetailsSteps extends Steps with NINOGenerator {

  private def toUserInfo(applicantDetails: DataTable): Option[UserInfo] = {
    val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    val data = applicantDetails.asMap(classOf[String], classOf[String]).asScala

      def getField(name: String): Option[String] = {
        val value = data.get(name) match {
          case Some(f) if f.equals("<eligible>") ⇒ generateEligibleNINO
          case Some(x)                           ⇒ x
          case None                              ⇒ ""
        }
        Some(value)
      }

    for {
      firstName ← getField("first name")
      lastName ← getField("last name")
      dateOfBirth ← getField("date of birth").map(s ⇒ LocalDate.parse(s, dateFormatter))
      email ← getField("email address")
      nino ← getField("NINO")
      address1 ← getField("address line 1")
      address2 ← getField("address line 2")
      address3 ← getField("address line 3")
      address4 ← getField("address line 4")
      address5 ← getField("address line 5")
      postcode ← getField("postcode")
      countryCode ← getField("country code")
    } yield UserInfo(firstName, lastName, nino, dateOfBirth, email,
                     Address(List(address1, address2, address3, address4, address5), Some(postcode), Some(countryCode)))

  }

  Given("""^an applicant has the following details:$"""){ (applicantDetails: DataTable) ⇒
    ScenarioContext.set("userInfo", toUserInfo(applicantDetails))
  }

  When("""^an applicant passes the eligibility check$"""){ () ⇒
    AuthorityWizardPage.enterUserDetails(200, "Strong", ScenarioContext.get[Option[UserInfo]]("userInfo"))
    AuthorityWizardPage.setRedirect(EligiblePage.url)
    AuthorityWizardPage.submit()
    EligiblePage.startCreatingAccount()
  }

  Then("""^they see their details$"""){ () ⇒
    val info: UserInfo = ScenarioContext.get[Option[UserInfo]]("userInfo").getOrElse(fail("User info not found"))
    val fullName = info.forename + " " + info.surname

    Helpers.isTextOnPage(fullName) shouldBe true
    Helpers.isTextOnPage(info.nino) shouldBe true

    val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val date = info.dateOfBirth.format(dateFormatter)

    Helpers.isTextOnPage(date) shouldBe true
    Helpers.isTextOnPage(info.email) shouldBe true
  }
}
