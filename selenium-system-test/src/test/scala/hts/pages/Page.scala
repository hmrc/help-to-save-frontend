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

package hts.pages

import hts.browser.Browser
import org.junit.Assert
import org.openqa.selenium.{By, WebDriver}
import uk.gov.hmrc.webdriver.SingletonDriver

trait Page {

  val expectedURL: String = ""

  var usedNino: String = ""

  var htsAccountNumber: String = ""

  val expectedPageTitle: Option[String] = None

  val expectedPageHeader: Option[String] = None

  implicit val driver: WebDriver = SingletonDriver.getInstance()

  def navigate()(implicit driver: WebDriver): Unit = Browser.go to expectedURL

  def checkForOldQuotes()(implicit driver: WebDriver): Unit = {
    val bodyText: String = driver.findElement(By.tagName("body")).getText
    Assert.assertFalse("Old single quotes were found!", bodyText.contains("'"))
    Assert.assertFalse("Old double quotes were found!", bodyText.contains('"'))
  }

}
