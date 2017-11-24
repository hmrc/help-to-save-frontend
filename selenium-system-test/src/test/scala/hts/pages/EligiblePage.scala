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

object EligiblePage extends Page {

  def navigate()(implicit driver: WebDriver): Unit = go to s"${Configuration.host}/help-to-save/eligible"

  override val expectedUrl: String = s"${Configuration.host}/help-to-save/eligible"

  override val expectedPageTitle: String = "You are eligible"

  override val expectedPageHeader: String = "You're eligible"

  val pageTitle: String = "You're eligible"
  val url: String = s"${Configuration.host}/help-to-save/check-eligibility"

  def startCreatingAccount()(implicit driver: WebDriver): Unit = click on xpath(".//*[@type='submit']")

  //def startCreatingYourAccount()(implicit driver: WebDriver): Unit = click on "start-creating-account"

  override def isCurrentPage(implicit driver: WebDriver): Boolean = checkHeader("h1", pageTitle)

  def detailsNotCorrect()(implicit driver: WebDriver): Unit = click on linkText("These details are not correct")

}
