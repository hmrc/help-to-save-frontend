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
import org.openqa.selenium.{By, Keys, WebDriver}
import org.scalatest.{Assertions, Matchers}
import org.scalatest.concurrent.{Eventually, PatienceConfiguration}
import org.scalatest.selenium.WebBrowser
import org.scalatest.time.{Millis, Seconds, Span}

trait Page extends Matchers
  with WebBrowser
  with Eventually
  with PatienceConfiguration
  with Assertions {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout  = scaled(Span(5, Seconds)), interval = scaled(Span(500, Millis)))

  def isCurrentPage(implicit driver: WebDriver): Boolean = false

  def back()(implicit driver: WebDriver): Unit = clickOn("ButtonBack")

  def nextPage()(implicit driver: WebDriver): Unit = {
    driver.findElement(By.id("get-help")).sendKeys(Keys.chord(Keys.CONTROL, Keys.END))
    find(CssSelectorQuery(".page-nav__link.page-nav__link--next")).foreach(click.on)
  }

  def checkHeader(heading: String, text: String)(implicit driver: WebDriver): Boolean =
    find(cssSelector(heading)).exists(_.text === text)

  def getCurrentUrl(implicit driver: WebDriver): String = driver.getCurrentUrl

  def getPageContent(implicit driver: WebDriver): String = driver.getPageSource

  def url(uri: String): String = s"${Configuration.host}/help-to-save/$uri"

  def navigate(uri: String)(implicit driver: WebDriver): Unit =
    go to url(uri)

  /*
 * CONFIRM CURRENT PAGE INFO
 */

  def expectedUrl: String = ""
  def expectedPageTitle: String = ""
  def expectedPageHeader: String = ""

  def pageHeading(implicit driver: WebDriver): String = {
    val heading = driver.findElement(By.tagName("h1")).getText
    if (heading != null) heading else ""
  }

  def pageInfoIsCorrect()(implicit driver: WebDriver): Unit = {
    expectedUrl shouldBe currentUrl
    expectedPageTitle shouldBe pageTitle
    expectedPageHeader shouldBe pageHeading
  }
}
