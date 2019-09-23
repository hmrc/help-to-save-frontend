/*
 * Copyright 2019 HM Revenue & Customs
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

package hts.pages.registrationPages

import hts.browser.Browser
import hts.pages.BasePage
import hts.pages.emailPages.SelectEmailPage
import hts.utils.{Configuration, TestBankDetails}
import org.openqa.selenium.WebDriver

object EligiblePage extends BasePage {

  override val expectedURL: String = s"${Configuration.host}/help-to-save/eligible"

  override val expectedPageTitle: Option[String] = Some("You’re eligible for a Help to Save account")

  override val expectedPageHeader: Option[String] = Some("You’re eligible for a Help to Save account")

  def continue()(implicit driver: WebDriver): Unit =
    Browser.clickButtonByIdOnceClickable("start-creating-account")

  def createAccountUsingGGEmail()(implicit driver: WebDriver): Unit = {
    EligiblePage.continue()
    SelectEmailPage.selectGGEmail()
    BankDetailsPage.enterDetails(TestBankDetails.ValidBankDetails)
    CheckDetailsCreateAccountPage.createAccount()
  }

  def createAccountError()(implicit driver: WebDriver): Unit = {
    EligiblePage.continue()
    SelectEmailPage.selectGGEmail()
  }

}
