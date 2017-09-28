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

package hts.pages

import hts.utils.Configuration
import org.openqa.selenium.WebDriver

object EligiblePage extends WebPage {

  override def expectedUrl: Option[String] = Some(s"${Configuration.host}/help-to-save/eligible")

  override def expectedPageTitle: Option[String] = Some("You are eligible")

  override def expectedPageHeader: Option[String] = Some("You're eligible")

  val pageTitle: String = "You're eligible"
  val url: String = s"${Configuration.host}/help-to-save/check-eligibility"

  def startCreatingAccount()(implicit driver: WebDriver): Unit = click on xpath(".//*[@type='submit']")

  override def isCurrentPage(implicit driver: WebDriver): Boolean = checkHeader("h1", pageTitle)

}
