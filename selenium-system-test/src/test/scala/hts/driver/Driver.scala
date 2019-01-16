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

package hts.driver

import java.net.URL
import java.util.concurrent.TimeUnit

import cats.instances.string._
import cats.syntax.either._
import cats.syntax.eq._
import hts.utils.Configuration.environment
import hts.utils.Environment.Local
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.{ChromeDriver, ChromeDriverService, ChromeOptions}
import org.openqa.selenium.firefox.{FirefoxDriver, FirefoxOptions}
import org.openqa.selenium.remote.{DesiredCapabilities, RemoteWebDriver}

object Driver extends Driver

class Driver {

  var sessionID = ""

  // set flag to true to see full information about browserstack webdrivers on initialisation
  private val DRIVER_INFO_FLAG = false

  private val systemProperties = System.getProperties

  def newWebDriver(): Either[String, WebDriver] = {
    val selectedDriver: Either[String, WebDriver] = Option(systemProperties.getProperty("browser")).map(_.toLowerCase) match {
      case Some("firefox") ⇒ Right(new FirefoxDriver())
      case Some("chrome") ⇒
        environment match {
          case Local ⇒
            Right(createChromeDriver(false))
          case _ ⇒ Right(new ChromeDriver())
        }
      case Some("remote-chrome")  ⇒ Right(createRemoteChrome)
      case Some("remote-firefox") ⇒ Right(createRemoteFirefox)
      case Some("zap-chrome")     ⇒ Right(createZapChromeDriver())
      case Some("browserstack")   ⇒ Right(createBrowserStackDriver)
      case Some("browserstack1")  ⇒ Right(createBrowserStackDriverOne)
      case Some("browserstack2")  ⇒ Right(createBrowserStackDriverTwo)
      case Some("browserstack3")  ⇒ Right(createBrowserStackDriverThree)
      case Some("browserstack4")  ⇒ Right(createBrowserStackDriverFour)
      case Some(other)            ⇒ Left(s"Unrecognised browser: $other")
      case None                   ⇒ Left("No browser set")
    }

    selectedDriver.foreach { driver ⇒
      val (_, _) = (sys.addShutdownHook(driver.quit()),
        driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS)
      )
    }
    selectedDriver
  }

  def createRemoteChrome: WebDriver = {
    new RemoteWebDriver(new URL("http://localhost:4444/wd/hub"), new ChromeOptions())
  }

  def createRemoteFirefox: WebDriver = {
    new RemoteWebDriver(new URL("http://localhost:4444/wd/hub"), new FirefoxOptions())
  }

  private val os: String =
    Option(systemProperties.getProperty("os.name")).getOrElse(sys.error("Could not read OS name"))

  private val isMac: Boolean = os.startsWith("Mac")

  private val isLinux: Boolean = os.startsWith("Linux")

  private val linuxArch: String =
    Option(systemProperties.getProperty("os.arch")).getOrElse(sys.error("Could not read OS arch"))

  private val driverDirectory: String = Option(systemProperties.getProperty("drivers")).getOrElse("/usr/local/bin")

  private def setChromeDriver() = {
    if (Option(systemProperties.getProperty("webdriver.driver")).isEmpty) {
      if (isMac) {
        systemProperties.setProperty("webdriver.driver", driverDirectory + "/chromedriver_mac")
      } else if (isLinux && linuxArch === "amd32") {
        systemProperties.setProperty("webdriver.driver", driverDirectory + "/chromedriver_linux32")
      } else if (isLinux) {
        systemProperties.setProperty("webdriver.driver", driverDirectory + "/chromedriver")
      } else {
        systemProperties.setProperty("webdriver.driver", driverDirectory + "/chromedriver.exe")
      }
    }
  }

  private def createChromeDriver(headless: Boolean): WebDriver = {
    setChromeDriver()
    
    val options = new ChromeOptions()
    options.addArguments("test-type")
    options.addArguments("--disable-gpu")
    if (headless) options.addArguments("--headless")

    val services = ChromeDriverService.createDefaultService()
    new ChromeDriver(services, options)
  }

  private def createZapChromeDriver(): WebDriver = {
    setChromeDriver()

    val options = new ChromeOptions()
    options.addArguments("test-type")
    options.addArguments("--proxy-server=http://localhost:11000")

    val services = ChromeDriverService.createDefaultService()
    val driver = new ChromeDriver(services, options)
    val caps = driver.getCapabilities
    driver
  }

  def createBrowserStackDriver: WebDriver = useBrowserStackDriverConstructor("test", default = true)

  def createBrowserStackDriverOne: WebDriver = useBrowserStackDriverConstructor("1", default = false)

  def createBrowserStackDriverTwo: WebDriver = useBrowserStackDriverConstructor("2", default = false)

  def createBrowserStackDriverThree: WebDriver = useBrowserStackDriverConstructor("3", default = false)

  def createBrowserStackDriverFour: WebDriver = useBrowserStackDriverConstructor("4", default = false)

  private def useBrowserStackDriverConstructor(identifier: String, default: Boolean): WebDriver = {
    val desiredCaps = new DesiredCapabilities()
    desiredCaps.setCapability("browserstack.debug", "true")
    desiredCaps.setCapability("browserstack.local", "true")
    if (!default) {
      desiredCaps.setCapability("browserstack.localIdentifier", identifier)
    }
    desiredCaps.setCapability("acceptSslCerts", "true")
    desiredCaps.setCapability("project", "HTS")
    desiredCaps.setCapability("build", "Local")

    List("browserstack.os",
      "browserstack.os_version",
      "browserstack.browser",
      "browserstack.device",
      "browserstack.browser_version",
      "browserstack.real_mobile")
      .map(k ⇒ (k, sys.props.get(k)))
      .collect({ case (k, Some(v)) ⇒ (k, v) })
      .foreach(x ⇒ desiredCaps.setCapability(x._1.replace("browserstack.", ""), x._2.replace("_", " ")))

    val username = sys.props.getOrElse("browserstack.username", "notspecified")
    val automateKey = sys.props.getOrElse("browserstack.key", "notspecified")
    val url = s"http://$username:$automateKey@hub.browserstack.com/wd/hub"

    val remoteDriver = new RemoteWebDriver(new URL(url), desiredCaps)
    remoteDriver
  }

}
