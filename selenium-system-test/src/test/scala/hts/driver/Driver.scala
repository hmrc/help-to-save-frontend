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

package src.test.scala.hts.driver

import java.util.concurrent.TimeUnit

import cats.syntax.either._
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.{ChromeDriver, ChromeOptions}
import org.openqa.selenium.firefox.{FirefoxDriver, MarionetteDriver}
import org.openqa.selenium.phantomjs.{PhantomJSDriver, PhantomJSDriverService}
import org.openqa.selenium.remote.{BrowserType, DesiredCapabilities}

object Driver {

  private val systemProperties = System.getProperties

  def newWebDriver(): Either[String, WebDriver] = {
    val selectedDriver: Either[String, WebDriver] = Option(systemProperties.getProperty("browser")).map(_.toLowerCase) match {
      case Some("chrome") ⇒ Right(createChromeDriver())
      case Some("phantomjs") ⇒ Right(createPhantomJsDriver())
      case Some(other) ⇒ Left(s"Unrecognised browser: $other")
      case None ⇒ Left("No browser set")
    }

    selectedDriver.map { driver ⇒
//      sys.addShutdownHook(driver.quit())
      driver.manage().window().maximize()
      driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS)
    }
    selectedDriver
  }

  private val os: String =
    Option(systemProperties.getProperty("os.name")).getOrElse(sys.error("Could not read OS name"))

  private val isMac: Boolean = os.startsWith("Mac")

  private val isLinux: Boolean = os.startsWith("Linux")

  private val linuxArch: String =
    Option(systemProperties.getProperty("os.arch")).getOrElse(sys.error("Could not read OS arch"))

  private val isJsEnabled: Boolean = true

  private val driverDirectory: String = "drivers"

  private def createChromeDriver(): WebDriver = {
    if (isMac) {
      systemProperties.setProperty("webdriver.chrome.driver", driverDirectory + "/chromedriver_mac")
    } else if (isLinux && linuxArch == "amd32") {
      systemProperties.setProperty("webdriver.chrome.driver", driverDirectory + "/chromedriver_linux32")
    } else if (isLinux) {
      systemProperties.setProperty("webdriver.chrome.driver", driverDirectory + "/chromedriver")
    } else {
      systemProperties.setProperty("webdriver.chrome.driver", driverDirectory + "/chromedriver.exe")
    }

    val capabilities = DesiredCapabilities.chrome()
    val options = new ChromeOptions()

    options.addArguments("test-type")
    options.addArguments("--disable-gpu")

    capabilities.setJavascriptEnabled(isJsEnabled)
    capabilities.setCapability(ChromeOptions.CAPABILITY, options)

    new ChromeDriver(capabilities)
  }


  private def createPhantomJsDriver(): WebDriver = {
    if (isMac) {
      systemProperties.setProperty("webdriver.phantomjs.binary", driverDirectory + "/phantomjs")
    } else if (isLinux && linuxArch == "amd32") {
      systemProperties.setProperty("webdriver.phantomjs.binary", driverDirectory + "/phantomjs_linux32")
    } else {
      systemProperties.setProperty("webdriver.phantomjs.binary", driverDirectory + "/phantomjs_linux64")
    }

    val capabilities = new DesiredCapabilities

    capabilities.setJavascriptEnabled(isJsEnabled)
    capabilities.setCapability(
      PhantomJSDriverService.PHANTOMJS_EXECUTABLE_PATH_PROPERTY,
      systemProperties.getProperty("webdriver.phantomjs.binary"))

    capabilities.setCapability(PhantomJSDriverService.PHANTOMJS_CLI_ARGS, Array("--ignore-ssl-errors=yes", "--web-security=false", "--ssl-protocol=any"))

    new PhantomJSDriver(capabilities)
  }

}
