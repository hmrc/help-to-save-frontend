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

package hts.utils

import hts.pages.Page
import org.openqa.selenium.support.ui.{ExpectedConditions, WebDriverWait}
import org.openqa.selenium.WebDriver

import scala.util.control.NonFatal

object Helpers extends Page {

  def isTextOnPage(regex: String)(implicit driver: WebDriver): Boolean = {
    val textPresent = regex.r.findAllIn(getPageContent).nonEmpty
    if (!(textPresent)) {
      println("Text not found: " + regex)
    }
    textPresent
  }

  def isActualUrlExpectedUrl(expectedUrl: String)(implicit driver: WebDriver): Boolean = {
    try {
      val wait = new WebDriverWait(driver, 20)
      wait.until(ExpectedConditions.urlContains(expectedUrl))
      true
    } catch {
      case NonFatal(_) ⇒ false
    }
  }
}
