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

import java.time.format.DateTimeFormatter

import cucumber.api.DataTable
import hts.pages.{AuthorityWizardPage, EligiblePage}
import hts.utils.{Helpers, ScenarioContext, TestUserInfo}
import hts.utils.EitherOps._

class ConfirmDetailsSteps extends Steps {

  Given("""^an applicant has the following details:$"""){ (applicantDetails: DataTable) ⇒
    ScenarioContext.setDataTable(applicantDetails)
  }

  When("""^an applicant passes the eligibility check$"""){ () ⇒
    AuthorityWizardPage.enterUserDetails(200, "Strong", ScenarioContext.userInfo().getOrElse(sys.error))
    AuthorityWizardPage.setRedirect(EligiblePage.url)
    AuthorityWizardPage.submit()
    EligiblePage.startCreatingAccount()
  }

  Then("""^they see their details$"""){ () ⇒
    val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    val info: TestUserInfo = ScenarioContext.userInfo().getOrElse(sys.error)
    val forename = info.forename.getOrElse(sys.error("Could not get forename"))
    val surname = info.surname.getOrElse(sys.error("Could not get surname"))
    val nino = info.nino.getOrElse(sys.error("Could not get NINO"))
    val email = info.email.getOrElse(sys.error("Could not get email"))
    val date = info.dateOfBirth.map(_.format(dateFormatter)).getOrElse(sys.error("Could not get date of birth"))

    val fullName = forename + " " + surname

    Helpers.isTextOnPage(fullName) shouldBe true
    Helpers.isTextOnPage(nino) shouldBe true

    Helpers.isTextOnPage(date) shouldBe true
    Helpers.isTextOnPage(email) shouldBe true
  }
}
