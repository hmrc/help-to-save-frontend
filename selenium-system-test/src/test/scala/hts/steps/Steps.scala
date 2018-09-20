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

package hts.steps

import java.util.concurrent.TimeUnit
import java.util.function.Function

import cats.syntax.either._
import cucumber.api.Scenario
import cucumber.api.scala.{EN, ScalaDsl}
import hts.driver.Driver
import hts.utils.ScenarioContext
import org.openqa.selenium._
import org.openqa.selenium.support.ui.{ExpectedConditions, WebDriverWait}
import org.scalatest.Matchers

private[steps] trait Steps extends ScalaDsl with EN with Matchers {

  import Steps._

  /** Tries to get the value of [[_driver]] - will throw an exception if it doesn't exist */
  implicit def driver: WebDriver = _driver.getOrElse(sys.error("Driver does not exist"))

  // create a new driver for each scenario
  Before { _ ⇒
    ScenarioContext.reset()

    if (_driver.isEmpty) {
      val d = Driver.newWebDriver()
        // map the left to Nothing
        .leftMap(e ⇒ sys.error(s"Could not find driver: $e"))
        // merge will merge Nothing and WebDriver to WebDriver since Nothing is a subtype of everything
        .merge
      val _ = d.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS)
      _driver = Some(d)
    }
  }

  After {
    scenario ⇒
      if (scenario.isFailed) {
        _driver.foreach{
          case driver: TakesScreenshot ⇒ takeScreenshot(scenario, driver)
          case _                       ⇒ println("Screenshot will not be taken")
        }
      }

      _driver.foreach(_.quit())
      _driver = None
  }

  private def takeScreenshot(scenario: Scenario, driver: WebDriver with TakesScreenshot): Unit = {
    try {
      val screenshot = driver.getScreenshotAs(OutputType.BYTES)
      scenario.embed(screenshot, "image/png")

      val failurePageUrl = driver.getCurrentUrl()

      // Try to take screen shot of previous page
      driver.navigate().back()

      val wait = new WebDriverWait(driver, 2) // scalastyle:ignore magic.number
      val expectedCondition = new Function[WebDriver, Boolean]() {
        override def apply(t: WebDriver): Boolean = {
          ExpectedConditions.not(ExpectedConditions.urlToBe(failurePageUrl)).apply(driver)
        }
      }

      wait.until(expectedCondition)
      val screenshotPreviousPage = driver.getScreenshotAs(OutputType.BYTES)
      scenario.embed(screenshotPreviousPage, "image/png")

    } catch {
      case e: TimeoutException   ⇒ println("Not possible to take screen shot of page before error.")
      case e: WebDriverException ⇒ System.err.println(s"Error creating screenshot: ${e.getMessage}")
    }
  }
}

private[steps] object Steps {

  /**
   * Each step definition file extends the `Steps` trait , but they will all reference this single driver
   * in the companion object. Having this variable in the trait would cause multiple drivers to be
   * created
   */
  private var _driver: Option[WebDriver] = None

}
