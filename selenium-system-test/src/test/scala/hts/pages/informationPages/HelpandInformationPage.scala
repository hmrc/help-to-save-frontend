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

package scala.hts.pages.informationPages

import hts.browser.Browser
import hts.pages.Page
import hts.pages.registrationPages.AccountCreatedPage
import hts.utils.Configuration
import org.junit.Assert
import org.openqa.selenium.{By, WebDriver}

object HelpandInformationPage extends Page {

  override val expectedURL: String = s"${Configuration.host}/help-to-save/account-home/help-information"
  override val expectedPageHeader: Option[String] = Some("Help and information")

  val paymentLink: By = By.xpath("//span[text()='Set up a regular payment or bank transfer']")
  val accountNumber: By = By.xpath("//li[contains(text(),'Enter the payment reference number')]/strong")

  def setupPaymentValidateHTSAccountNumber()(implicit driver: WebDriver): Unit = {
    Browser.clickOn(driver.findElement(paymentLink))
    Assert.assertEquals("HTS Account Number validation", {
      AccountCreatedPage.htsAccountNumber
    }, Browser.getText(accountNumber))
  }

}
