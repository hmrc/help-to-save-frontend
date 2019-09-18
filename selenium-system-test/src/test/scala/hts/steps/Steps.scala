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

package hts.steps

import java.io.File

import cucumber.api.Scenario
import cucumber.api.scala.{EN, ScalaDsl}
import hts.pages.Page
import hts.utils.{Configuration, ScenarioContext}
import org.apache.commons.io.FileUtils
import org.openqa.selenium.{OutputType, TakesScreenshot, WebDriver}
import org.scalatest.Matchers
import org.scalatest.selenium.WebBrowser._

private[steps] trait Steps extends ScalaDsl with EN with Matchers with Page {

  // create a new driver for each scenario
  Before { scenario ⇒
    ScenarioContext.reset()
    driver.manage().deleteAllCookies()
    if (scenario.getName.equalsIgnoreCase("An unauthenticated user creates new account")) {
      driver.get(s"${Configuration.authHost}/auth-login-stub/session/logout")
    }
  }

  After { scenario ⇒

    if (scenario.isFailed) {
      driver match {
        case a: TakesScreenshot ⇒
          takeScreenshot(scenario, "-page-on-failure", a)
          println(s"Page of failure was: $currentUrl")
          a.navigate().back()
          takeScreenshot(scenario, "-previous-page", a)
          println(s"Previous page was: $currentUrl")
      }
    }

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
}
