/*
 * Copyright 2018 HM Revenue & Customs
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
import hts.browser.Browser
import hts.pages.{AuthorityWizardPage, EligiblePage, Page}
import hts.utils.{ScenarioContext, TestUserInfo}
import hts.utils.EitherOps._

class ConfirmDetailsSteps extends Steps {

  Given("""^an applicant has the following details:$"""){ (applicantDetails: DataTable) ⇒
    ScenarioContext.setDataTable(applicantDetails)
  }

  When("""^an applicant passes the eligibility check$"""){ () ⇒
    AuthorityWizardPage.enterUserDetails(200, "Strong", ScenarioContext.userInfo().getOrElse(sys.error))
    AuthorityWizardPage.setRedirect(EligiblePage.expectedURL)
    AuthorityWizardPage.submit()
  }

  Then("""^they see their details$"""){ () ⇒
    val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")

    val nino = ScenarioContext.currentNINO

    val info: TestUserInfo = ScenarioContext.userInfo().getOrElse(sys.error)
    val forename = info.forename.getOrElse(sys.error("Could not get forename"))
    val surname = info.surname.getOrElse(sys.error("Could not get surname"))
    val date = info.dateOfBirth.map(_.format(dateFormatter)).getOrElse(sys.error("Could not get date of birth"))

    val fullName = forename + " " + surname
    val displayedNino = nino.grouped(2).mkString(" ")

    Browser.checkCurrentPageIs(EligiblePage)
    Browser.isTextOnPage(fullName) shouldBe true
    Browser.isTextOnPage(displayedNino) shouldBe true
    Browser.isTextOnPage(date) shouldBe true
  }
}
