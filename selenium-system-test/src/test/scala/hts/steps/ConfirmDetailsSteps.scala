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
import hts.utils.EitherOps._
import hts.pages._
import hts.utils.{ScenarioContext, TestUserInfo}

class ConfirmDetailsSteps extends Steps {

  Given("^an applicant has the following details:$") { (applicantDetails: DataTable) ⇒
    ScenarioContext.setDataTable(applicantDetails, ScenarioContext.generateEligibleNINO())
  }

  When("^an applicant passes the eligibility check$") {
    AuthorityWizardPage.enterUserDetails(200, "Strong", ScenarioContext.userInfo().getOrElse(sys.error))
  }

  When("^has entered their bank details$") {
    AuthorityWizardPage.enterUserDetails(200, "Strong", ScenarioContext.userInfo().getOrElse(sys.error))
    EligiblePage.clickConfirmAndContinue()
    SelectEmailPage.selectGGEmail()
    val info: TestUserInfo = ScenarioContext.userInfo().getOrElse(sys.error)
    BankAccountDetailsPage.enterAccountName(info.bankDetails.accountName.getOrElse(sys.error("Could not get bank account name")))
    BankAccountDetailsPage.enterSortCode(info.bankDetails.sortCode.getOrElse(sys.error("Could not get sort code")))
    BankAccountDetailsPage.enterAccountNumber(info.bankDetails.accountNumber.getOrElse(sys.error("Could not get bank account number")))
    BankAccountDetailsPage.enterRollNumber(info.bankDetails.rollNumber.getOrElse(sys.error("Could not get roll number")))
    BankAccountDetailsPage.continue()
  }

  Then("^they see their details$") { () ⇒
    val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")
    println("Marcus just about to call ScenarioContext.currentNINO. Nino is: " + ScenarioContext.currentNINO)
    val nino = ScenarioContext.currentNINO
    println("Marcus just called ScenarioContext.currentNINO. Nino is: " + ScenarioContext.currentNINO)
    val info: TestUserInfo = ScenarioContext.userInfo().getOrElse(sys.error)
    val forename = info.forename.getOrElse(sys.error("Could not get forename"))
    val surname = info.surname.getOrElse(sys.error("Could not get surname"))
    val date = info.dateOfBirth.map(_.format(dateFormatter)).getOrElse(sys.error("Could not get date of birth"))
    val fullName = forename + " " + surname
    val displayedNino = nino.grouped(2).mkString(" ")
    val email = info.email.getOrElse(sys.error("Could not get email"))
    val sortCode = info.bankDetails.sortCode.getOrElse(sys.error("Could not get sort code")).filterNot("-".toSet).grouped(2).mkString(" ")
    val accountNumber = info.bankDetails.accountNumber.getOrElse(sys.error("Could not get account number"))
    val rollNumber = info.bankDetails.rollNumber.getOrElse(sys.error("Could not get roll number"))
    val accountName = info.bankDetails.accountName.getOrElse(sys.error("Could not get account name"))

    Browser.checkCurrentPageIs(CheckDetailsPage)
    CheckDetailsPage.checkForOldQuotes()
    Browser.isTextOnPage(fullName) shouldBe Right(Set(fullName))
    Thread.sleep(10000000)
    Browser.isTextOnPage(displayedNino) shouldBe Right(Set(displayedNino))
    Browser.isTextOnPage(date) shouldBe Right(Set(date))
    Browser.isTextOnPage(email) shouldBe Right(Set(email))
    Browser.isTextOnPage(sortCode) shouldBe Right(Set(sortCode))
    Browser.isTextOnPage(rollNumber) shouldBe Right(Set(rollNumber))
    Browser.isTextOnPage(accountName) shouldBe Right(Set(accountName))
  }
}
