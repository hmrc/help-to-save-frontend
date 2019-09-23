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
import hts.utils.Configuration
import org.openqa.selenium.WebDriver

object CheckDetailsCreateAccountPage extends BasePage {

  override val expectedURL: String = s"${Configuration.host}/help-to-save/create-account"

  override val expectedPageHeader: Option[String] = Some("Create a Help to Save account")

  override val expectedPageTitle: Option[String] = Some("Create a Help to Save account")

  def detailsNotCorrect()(implicit driver: WebDriver): Unit =
    Browser.clickButtonByIdOnceClickable("change-details-are-incorrect")

  def emailNotCorrect()(implicit driver: WebDriver): Unit =
    Browser.clickButtonByIdOnceClickable("change-email")

  def bankDetailsNotCorrect()(implicit driver: WebDriver): Unit =
    Browser.clickButtonByIdOnceClickable("change-bank-details")

  def createAccount()(implicit driver: WebDriver): Unit =
    Browser.clickButtonByIdOnceClickable("accept-and-create-account")
}
