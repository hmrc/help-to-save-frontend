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

import java.io.File

import cucumber.api.Scenario
import cucumber.api.scala.{EN, ScalaDsl}
import hts.browser.Browser
import hts.utils.{Configuration, ScenarioContext}
import org.apache.commons.io.FileUtils
import org.junit.Assert
import org.openqa.selenium.{By, OutputType, TakesScreenshot, WebDriver}
import org.scalatest.Assertion
import org.scalatest.Matchers
import uk.gov.hmrc.webdriver.SingletonDriver

import scala.hts.utils.TestVariables

trait BasePage extends ScalaDsl with EN with Matchers with Assertion {

  val expectedURL: String = ""

  var usedNino: String = ""

  var htsAccountNumber: String = ""

  val expectedPageTitle: Option[String] = None

  val expectedPageHeader: Option[String] = None

  implicit val driver: WebDriver = SingletonDriver.getInstance()

  lazy val generalHTSContactPage:String = "https://www.gov.uk/government/organisations/hm-revenue-customs/contact/help-to-save-scheme"

  def navigate()(implicit driver: WebDriver): Unit = Browser.go to expectedURL

  def checkForOldQuotes()(implicit driver: WebDriver): Unit = {
    val bodyText: String = driver.findElement(By.tagName("body")).getText
    Assert.assertFalse("Old single quotes were found!", bodyText.contains("'"))
    Assert.assertFalse("Old double quotes were found!", bodyText.contains('"'))
  }

  // To take a screeshot and embed in to the Cucumber report
  private def takeScreenshot(scenario: Scenario, s: String, dr: WebDriver with TakesScreenshot): Unit = {
    val name = scenario.getName

    if (!new java.io.File(s"./target/screenshots/$name$s.png").exists) {
      dr.manage().window().maximize()
      val scr = dr.getScreenshotAs(OutputType.FILE)
      FileUtils.copyFile(scr, new File(s"./target/screenshots/$name$s.png"))
      val byteFile = dr.getScreenshotAs(OutputType.BYTES)
      scenario.embed(byteFile, "image/png")
    }
  }

  Before { _ ⇒
    if (TestVariables.scenarioLoop) {
      ScenarioContext.reset()
      driver.manage().deleteAllCookies()
      driver.get(s"${Configuration.authHost}/auth-login-stub/session/logout")
      TestVariables.scenarioLoop = false
    }
  }

  After { scenario ⇒
    if (!TestVariables.scenarioLoop) {
      TestVariables.scenarioLoop = true
      if (scenario.isFailed) {
        driver match {
          case a: TakesScreenshot ⇒
            takeScreenshot(scenario, "-page-on-failure", a)
            println(s"Page of failure was: ${Browser.getCurrentUrl}")
            a.navigate().back()
            takeScreenshot(scenario, "-previous-page", a)
            println(s"Previous page was: ${Browser.getCurrentUrl}")
        }
      }
    }
  }

}
