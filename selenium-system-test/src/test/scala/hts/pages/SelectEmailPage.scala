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
import hts.utils.Configuration
import org.openqa.selenium.WebDriver

object SelectEmailPage extends Page {

  val expectedURL: String = s"${Configuration.host}/help-to-save/select-email"

  override val expectedPageHeader: Option[String] = Some("Which email address do you want us to use for your Help to Save account?")

  override val expectedPageTitle: Option[String] = Some("Do you want to use the email address we hold for you?")

  def clickContinue()(implicit driver: WebDriver): Unit =
    Browser.find(Browser.className("button")).foreach(_.underlying.click())

  def selectGGEmail()(implicit driver: WebDriver): Unit = {
    Browser.clickButtonByIdOnceClickable("registered-email")
    clickContinue()
  }

  def setAndVerifyNewEmail(email: String)(implicit driver: WebDriver): Unit = {
    Browser.clickButtonByIdOnceClickable("add-new-email")
    Browser.textField("new-email").value = email
    clickContinue()
  }

}
