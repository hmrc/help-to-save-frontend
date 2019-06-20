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

package hts.browser

import java.util.function.Function

import hts.pages.Page
import hts.pages.informationPages.{FeedbackPage, PrivacyPolicyPage}
import hts.utils.Configuration
import org.openqa.selenium._
import org.openqa.selenium.support.ui._
import org.scalatest.Matchers
import org.scalatest.selenium.WebBrowser

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

object Browser extends WebBrowser with Navigation with Retrievals with Assertions with Matchers {
  def checkForLinksThatExistOnEveryPage(page: Page)(implicit driver: WebDriver): Unit = {
    checkCurrentPageIs(page)
    clickButtonByIdOnceClickable("feedback-link")
    checkCurrentPageIs(FeedbackPage)

    goBack()
    clickButtonByIdOnceClickable("get-help-action")
    isElementByIdVisible("report-error-partial-form") shouldBe true
    openAndCheckPageInNewWindowUsingLinkText("Privacy policy", PrivacyPolicyPage)

    page.navigate()
    checkCurrentPageIs(page)
  }
}

trait Navigation {
  this: WebBrowser ⇒

  def navigateTo(uri: String)(implicit driver: WebDriver): Unit =
    go to s"${Configuration.host}/help-to-save/$uri"

  def nextPage()(implicit driver: WebDriver): Unit = {
    driver.findElement(By.id("next")).sendKeys(Keys.chord(Keys.CONTROL, Keys.END))
    find(id("next")).foreach(click.on)
  }

  def clickButtonByIdOnceClickable(id: String)(implicit driver: WebDriver): Unit =
    clickByIdentifier(id, By.id)(click.on)

  def clickButtonByClassOnceClickable(className: String)(implicit driver: WebDriver): Unit =
    clickByIdentifier(className, By.className)(click.on)

  def clickLinkTextOnceClickable(text: String)(implicit driver: WebDriver): Unit =
    clickByIdentifier(text, By.linkText)(s ⇒ click on linkText(s))

  def scrollToElement(id: String, By: String ⇒ By)(implicit driver: WebDriver): Unit = {
    val elementLocation = driver.findElement(By(id)).getLocation
    driver match {
      case executor: JavascriptExecutor ⇒
        val diff = {
          val d = elementLocation.getY - executor.executeScript("return window.pageYOffset;").toString.toFloat.toInt
          if (d < 0) {
            d - 200 // done to avoid any bars or popups blocking elements on mobile devices
          } else {
            d + 200 // done to avoid any bars or popups blocking elements on mobile devices
          }
        }
        executor.executeScript("scrollBy(0," + diff.toString + ")")
    }
  }

  private def clickByIdentifier(id: String, by: String ⇒ By)(clickOn: String ⇒ Unit)(implicit driver: WebDriver): Unit = {
    val wait = new WebDriverWait(driver, 20) // scalastyle:ignore magic.number
    val expectedCondition = new Function[WebDriver, Boolean]() {
      override def apply(t: WebDriver): Boolean = {
        ExpectedConditions.or(
          ExpectedConditions.elementToBeClickable(by(id)),
          ExpectedConditions.presenceOfElementLocated(by(id)),
          ExpectedConditions.visibilityOfElementLocated(by(id))).apply(driver)
      }
    }

    wait.until(expectedCondition)
    clickOn(id)
  }

}

trait Retrievals {
  this: WebBrowser ⇒

  def getCurrentUrl(implicit driver: WebDriver): String = driver.getCurrentUrl

  def getPageContent(implicit driver: WebDriver): String = driver.getPageSource

  def getPageHeading(implicit driver: WebDriver): String =
    Option(driver.findElement(By.tagName("h1")).getText).getOrElse("")

  def getText(query: By)(implicit driver: WebDriver): String =
    Option(driver.findElement(query).getText).getOrElse("")
}

trait Assertions {
  this: WebBrowser with Retrievals with Matchers ⇒

  def isTextOnPage(regex: String)(implicit driver: WebDriver): Either[String, Set[String]] = {
    val textPresent = regex.r.findAllIn(Browser.getPageContent)
    if (textPresent.isEmpty) {
      Left(s"Text not found: $regex")
    } else {
      Right(textPresent.toSet)
    }
  }

  //default titleSuffix is for Help to Save pages but can be defined for GOV.UK pages
  def checkCurrentPageIs(page: Page, titleSuffix: String = "Help to Save - GOV.UK")(implicit driver: WebDriver): Unit = {
      def isActualUrlExpectedUrl(expectedUrl: String)(implicit driver: WebDriver): Boolean = {
        try {
          val wait = new WebDriverWait(driver, 20) // scalastyle:ignore magic.number
          val expectedCondition = new Function[WebDriver, Boolean]() {
            override def apply(t: WebDriver): Boolean = {
              ExpectedConditions.or(
                ExpectedConditions.urlContains(expectedUrl)).apply(driver)
            }
          }
          wait.until(expectedCondition)
          true
        } catch {
          case NonFatal(_) ⇒ false
        }
      }

    val urlMatches =
      Either.cond(isActualUrlExpectedUrl(page.expectedURL),
        (),
        s"Expected URL was ${page.expectedURL}, but actual URL was " + driver.getCurrentUrl()
      )

    urlMatches shouldBe Right(())
    page.expectedPageTitle.foreach(t ⇒ pageTitle shouldBe s"$t - $titleSuffix")
    page.expectedPageHeader.foreach(getPageHeading shouldBe _)
  }

  def checkForBadContent(page: Page)(implicit driver: WebDriver): Unit = {
    checkCurrentPageIs(page)
    // check for unmapped message keys on the page
    isTextOnPage("hts(\\..+)+").fold(
      _ ⇒ (),
      s ⇒ fail(s"Found unescaped message keys on page: [${s.mkString("; ")}]")
    )
  }

  def checkHeader(page: Page)(implicit driver: WebDriver): Unit =
    page.expectedPageHeader.foreach(getPageHeading shouldBe _)

  def scrollDown()(implicit driver: WebDriver): AnyRef = driver match {
    case executor: JavascriptExecutor ⇒
      executor.executeScript("scrollBy(0,250)")
    case _ ⇒ fail("Failed to scroll down")
  }

  def checkPageIsLoaded()(implicit driver: WebDriver): Unit = {
    val wait = new WebDriverWait(driver, 20) // scalastyle:ignore magic.number
    val expectedCondition = new Function[WebDriver, Object]() {
      override def apply(t: WebDriver): Object = {
        ExpectedConditions.jsReturnsValue("return document.readyState === 'complete';").apply(driver)
      }
    }
    wait.until(expectedCondition)
  }

  def openAndCheckPageInNewWindowUsingLinkText(linkText: String, page: Page)(implicit driver: WebDriver): Unit = {
    Browser.clickLinkTextOnceClickable(linkText)
    val tabs = driver.getWindowHandles.asScala.toList
    tabs match {
      case tab1 :: tab2 :: Nil ⇒
        driver.switchTo.window(tab2) //switch to privacy policy tab
        Browser.checkCurrentPageIs(page)
        driver.close()
        driver.switchTo.window(tab1) //switch back to original page
      case ts ⇒ fail(s"Unexpected number of tabs: ${ts.length}")
    }
  }

  def isElementByIdVisible(id: String)(implicit driver: WebDriver): Boolean = {
    driver.findElement(By.id(id)).isDisplayed
  }

}
