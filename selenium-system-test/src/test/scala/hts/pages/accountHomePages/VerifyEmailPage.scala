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

package hts.pages.accountHomePages

import hts.browser.Browser
import hts.pages.Page
import hts.utils.Configuration
import org.openqa.selenium.WebDriver

object VerifyEmailPage extends Page {

  val expectedURL: String = s"${Configuration.host}/help-to-save/account-home/verify-email"

  override val expectedPageTitle: Option[String] = Some("You have 30 minutes to verify your email address - Manage your email address")

  override val expectedPageHeader: Option[String] = Some("You have 30 minutes to verify your email address")

  def resendEmail(implicit driver: WebDriver): Unit = Browser.clickButtonByIdOnceClickable("resend-verification")

  def goToAccountHome(implicit driver: WebDriver): Unit = Browser.clickLinkTextOnceClickable("Go to account home")

}
