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
import hts.pages.Page
import hts.utils.Configuration
import org.openqa.selenium.{By, WebDriver}

object AccountCreatedPage extends Page {

  override val expectedURL: String = s"${Configuration.host}/help-to-save/account-created"
  override val expectedPageHeader: Option[String] = Some("Help to Save account created")

  val accountNumber: By = By.xpath("//span[contains(text(),'Your account number is')]/strong")

  def clickSignOut(implicit driver: WebDriver): Unit = Browser.clickButtonByIdOnceClickable("nav-sign-out")

  def retrieveHTSAccountNumber(implicit driver: WebDriver): Unit = {
    htsAccountNumber = Browser.getText(accountNumber)
    println(s"HTS account created = $htsAccountNumber \n")
  }
}
