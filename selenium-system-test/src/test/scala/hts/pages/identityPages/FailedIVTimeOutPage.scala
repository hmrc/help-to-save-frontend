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

package hts.pages.identityPages

import hts.browser.Browser
import hts.pages.Page
import hts.utils.Configuration
import org.openqa.selenium.WebDriver

object FailedIVTimeOutPage extends Page {

  override val expectedURL: String = s"${Configuration.host}/help-to-save/failed-iv-time-out"

  override val expectedPageHeader: Option[String] = Some("Your session has ended")

  override val expectedPageTitle: Option[String] = Some("Your session has ended")

  def tryAgain()(implicit driver: WebDriver): Unit = Browser.click on Browser.linkText("Try again")

}
