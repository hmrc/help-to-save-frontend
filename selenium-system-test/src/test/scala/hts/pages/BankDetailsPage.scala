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

package hts.pages

import hts.browser.Browser
import hts.utils.{Configuration, TestBankDetails}
import org.openqa.selenium.WebDriver

object BankDetailsPage extends Page {

  val expectedURL: String = s"${Configuration.host}/help-to-save/enter-uk-bank-details"

  override val expectedPageHeader: Option[String] = Some("Which UK bank account do you want us to pay your bonuses and withdrawals into?")

  override val expectedPageTitle: Option[String] = Some("Which UK bank account do you want us to pay your bonuses and withdrawals into?")

  def enterDetails(bankDetails: TestBankDetails)(implicit driver: WebDriver): Unit = {
    bankDetails.sortCode.foreach(enterSortCode)
    bankDetails.accountNumber.foreach(enterAccountNumber)
    bankDetails.accountName.foreach(enterAccountName)
    bankDetails.rollNumber.foreach(enterRollNumber)
    continue()
  }

  def enterSortCode(sortCode: String)(implicit driver: WebDriver) = Browser.telField("sortCode").value = sortCode
  def enterAccountNumber(accountNumber: String)(implicit driver: WebDriver) = Browser.telField("accountNumber").value = accountNumber
  def enterRollNumber(rollNumber: String)(implicit driver: WebDriver) = Browser.telField("rollNumber").value = rollNumber
  def enterAccountName(accountName: String)(implicit driver: WebDriver) = Browser.telField("accountName").value = accountName

  def continue()(implicit driver: WebDriver): Unit = Browser.clickButtonByIdOnceClickable("bankDetailsSubmit")

}