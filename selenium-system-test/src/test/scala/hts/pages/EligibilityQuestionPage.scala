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

object EligibilityQuestionPage extends WebPage {

  override def expectedUrl: String = s"${Configuration.host}/help-to-save/access-account"

  override def expectedPageTitle: String = "TBD"

  override def expectedPageHeader: String = "TBD"

  //TODO This page is still under construction
  val pageTitle: String = "\uD83D\uDE1E You don't have an account \uD83D\uDE22"

  def clickCheckEligibility()(implicit driver: WebDriver): Unit = click on "continue"

  override def isCurrentPage(implicit driver: WebDriver): Boolean = checkHeader("h2", pageTitle)
}
