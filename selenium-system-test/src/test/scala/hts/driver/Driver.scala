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

package hts.driver

import java.util.concurrent.TimeUnit

import cats.instances.string._
import cats.syntax.eq._
import cats.syntax.either._
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.{ChromeDriver, ChromeOptions}
import org.openqa.selenium.remote.DesiredCapabilities

object Driver {

  private val systemProperties = System.getProperties

  def newWebDriver(): Either[String, WebDriver] = {
    val selectedDriver: Either[String, WebDriver] = Option(systemProperties.getProperty("browser")).map(_.toLowerCase) match {
      case Some("chrome")       ⇒ Right(createChromeDriver(false))
      case Some("zap-chrome")   ⇒ Right(createZapChromeDriver())
      case Some("headless")     ⇒ Right(createChromeDriver(true))
      case Some("zap-headless") ⇒ Right(createZapHeadlessChromeDriver)
      case Some(other)          ⇒ Left(s"Unrecognised browser: $other")
      case None                 ⇒ Left("No browser set")
    }

    selectedDriver.foreach { driver ⇒
      val (_, _, _) = (sys.addShutdownHook(driver.quit()),
        driver.manage().window().maximize(),
        driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS)
      )
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

  private val driverDirectory: String = Option(systemProperties.getProperty("drivers")).getOrElse("/usr/local/bin")

//  val webDriver = newWebDriver() match {
//    case Left(x) ⇒
//  }

  private def setChromeDriver() = {
    if (Option(systemProperties.getProperty("webdriver.chrome.driver")).isEmpty) {
      if (isMac) {
        systemProperties.setProperty("webdriver.chrome.driver", driverDirectory + "/chromedriver_mac")
      } else if (isLinux && linuxArch === "amd32") {
        systemProperties.setProperty("webdriver.chrome.driver", driverDirectory + "/chromedriver_linux32")
      } else if (isLinux) {
        systemProperties.setProperty("webdriver.chrome.driver", driverDirectory + "/chromedriver")
      } else {
        systemProperties.setProperty("webdriver.chrome.driver", driverDirectory + "/chromedriver.exe")
      }
    }
  }

  private def createChromeDriver(headless: Boolean): WebDriver = {
    setChromeDriver

    val capabilities = DesiredCapabilities.chrome()
    val options = new ChromeOptions()
    options.addArguments("test-type")
    options.addArguments("--disable-gpu")
    if (headless) options.addArguments("--headless")
    capabilities.setJavascriptEnabled(isJsEnabled)
    capabilities.setCapability(ChromeOptions.CAPABILITY, options)
    new ChromeDriver(capabilities)
  }

  private def createZapChromeDriver(): WebDriver = {
    setChromeDriver

    val capabilities = DesiredCapabilities.chrome()
    val options = new ChromeOptions()
    options.addArguments("test-type")
    options.addArguments("--proxy-server=http://localhost:11000")
    capabilities.setCapability(ChromeOptions.CAPABILITY, options)
    val driver = new ChromeDriver(capabilities)
    val caps = driver.getCapabilities
    driver
  }

  private def createZapHeadlessChromeDriver(): WebDriver = {
    setChromeDriver

    val capabilities = DesiredCapabilities.chrome()
    val options = new ChromeOptions()
    options.addArguments("test-type")
    options.addArguments("--proxy-server=http://localhost:11000")
    options.addArguments("--headless")
    capabilities.setCapability(ChromeOptions.CAPABILITY, options)
    val driver = new ChromeDriver(capabilities)
    val caps = driver.getCapabilities
    driver
  }
}
