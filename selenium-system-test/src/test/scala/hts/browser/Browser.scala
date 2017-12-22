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

package hts.browser

import hts.pages.Page
import hts.pages.identityPages.{FailedIVInsufficientEvidencePage, FailedIVTechnicalIssuePage, IdentityVerifiedPage}
import hts.utils.Configuration
import org.openqa.selenium.support.ui.{ExpectedConditions, WebDriverWait}
import org.openqa.selenium.{By, Keys, WebDriver}
import org.scalatest.Matchers
import org.scalatest.selenium.WebBrowser

import scala.util.control.NonFatal

object Browser extends WebBrowser with Navigation with Retrievals with Assertions with Matchers

trait Navigation { this: WebBrowser ⇒

  def navigateTo(uri: String)(implicit driver: WebDriver): Unit =
    go to s"${Configuration.host}/help-to-save/$uri"

  def back()(implicit driver: WebDriver): Unit = clickOn("ButtonBack")

  def nextPage()(implicit driver: WebDriver): Unit = {
    driver.findElement(By.id("next")).sendKeys(Keys.chord(Keys.CONTROL, Keys.END))
    find(CssSelectorQuery(".page-nav__link.page-nav__link--next")).foreach(click.on)
  }

  def clickButtonByIdOnceClickable(id: String)(implicit driver: WebDriver): Unit =
    clickByIdentifier(id, By.id)

  def clickButtonByClassOnceClickable(className: String)(implicit driver: WebDriver): Unit =
    clickByIdentifier(className, By.className)

  private def clickByIdentifier(id: String, by: String ⇒ By)(implicit driver: WebDriver): Unit = {
    val wait = new WebDriverWait(driver, 20)
    wait.until(ExpectedConditions.or(
      ExpectedConditions.elementToBeClickable(by(id)),
      ExpectedConditions.presenceOfElementLocated(by(id)),
      ExpectedConditions.visibilityOfElementLocated(by(id))))
    click on id
  }

}

trait Retrievals { this: WebBrowser ⇒

  def getCurrentUrl(implicit driver: WebDriver): String = driver.getCurrentUrl

  def getPageContent(implicit driver: WebDriver): String = driver.getPageSource

  def getPageHeading(implicit driver: WebDriver): String =
    Option(driver.findElement(By.tagName("h1")).getText).getOrElse("")

}

trait Assertions { this: WebBrowser with Retrievals with Matchers ⇒

  def isTextOnPage(regex: String)(implicit driver: WebDriver): Boolean = {
    val textPresent = regex.r.findAllIn(Browser.getPageContent).nonEmpty
    if (!textPresent) {
      println("Text not found: " + regex)
    }
    textPresent
  }

  def checkCurrentPageIs(page: Page)(implicit driver: WebDriver): Unit = {
      def isActualUrlExpectedUrl(expectedUrl: String)(implicit driver: WebDriver): Boolean = {
        try {
          val wait = new WebDriverWait(driver, 20)
          wait.until(ExpectedConditions.urlContains(expectedUrl))
          true
        } catch {
          case NonFatal(_) ⇒ false
        }
      }

    isActualUrlExpectedUrl(page.expectedURL) shouldBe true
    page.expectedPageTitle.foreach(t ⇒ pageTitle shouldBe s"$t - Help to Save - GOV.UK")
    page.expectedPageHeader.foreach(getPageHeading shouldBe _)
  }

}
