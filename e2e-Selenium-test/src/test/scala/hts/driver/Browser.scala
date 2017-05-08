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

import org.apache.commons.lang3.StringUtils
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.{WebDriver, WebDriverException}
import org.openqa.selenium.firefox.MarionetteDriver
import org.openqa.selenium.phantomjs.{PhantomJSDriver, PhantomJSDriverService}
import org.openqa.selenium.remote.{BrowserType, DesiredCapabilities}
import org.scalatest.selenium.{Chrome, Firefox}

import org.openqa.selenium.WebDriver.Window

object Browser extends StartUpTearDown {

  def getOs = System.getProperty("os.name")

  val systemProperties = System.getProperties
  val isMac: Boolean = getOs.startsWith("Mac")
  val isLinux: Boolean = getOs.startsWith("Linux")
  val linuxArch = systemProperties.getProperty("os.arch")
  val isJsEnabled: Boolean = true

  val driverDirectory: String = "e2e-Selenium-test/drivers"

  def javascriptEnabled: Boolean =
    !StringUtils.isEmpty(systemProperties.getProperty("javascriptEnabled"))

  def maximizeWindow(window: Window): Unit = {
    try {
      window.maximize()
    }
    catch {
      case _: WebDriverException => // Swallow exception
    }
  }

  def createGeckoDriver(): WebDriver = {
    if (isMac)
      systemProperties.setProperty("webdriver.gecko.driver", driverDirectory + "/geckodriver_mac")
    else if (isLinux)
      systemProperties.setProperty("webdriver.gecko.driver", driverDirectory + "/geckodriver_linux64")
    else
      systemProperties.setProperty("webdriver.gecko.driver", driverDirectory + "/geckodriver.exe")

    val capabilities = DesiredCapabilities.firefox()
    capabilities.setJavascriptEnabled(isJsEnabled)

    val driver = new MarionetteDriver()
    maximizeWindow(driver.manage().window())

    driver
  }

  def createFirefoxDriver(): WebDriver = {
    val capabilities = DesiredCapabilities.firefox()
    capabilities.setJavascriptEnabled(true)
    val driver = Firefox.webDriver
    capabilities.setBrowserName(BrowserType.FIREFOX)
    maximizeWindow(driver.manage().window())
    driver
  }

  def createChromeDriver(): WebDriver = {
    if (isMac)
      systemProperties.setProperty("webdriver.chrome.driver", driverDirectory + "/chromedriver_mac")
    else if (isLinux && linuxArch == "amd32")
      systemProperties.setProperty("webdriver.chrome.driver", driverDirectory + "/chromedriver_linux32")
    else if (isLinux)
      systemProperties.setProperty("webdriver.chrome.driver", driverDirectory + "/chromedriver")
    else
      systemProperties.setProperty("webdriver.chrome.driver", driverDirectory + "/chromedriver.exe")

    val capabilities = DesiredCapabilities.chrome()
    val options = new ChromeOptions()

    options.addArguments("test-type")
    options.addArguments("--disable-gpu")

    capabilities.setJavascriptEnabled(isJsEnabled)
    capabilities.setCapability(ChromeOptions.CAPABILITY, options)

    val driver = Chrome.webDriver
    maximizeWindow(driver.manage().window())
    driver
  }


  def createPhantomJsDriver(): WebDriver = {
    if (isMac)
      systemProperties.setProperty("webdriver.phantomjs.binary", driverDirectory + "/phantomjs")
    else if (isLinux && linuxArch == "amd32")
      systemProperties.setProperty("webdriver.phantomjs.binary", driverDirectory + "/phantomjs_linux32")
    else
      systemProperties.setProperty("webdriver.phantomjs.binary", driverDirectory + "/phantomjs_linux64")

    val capabilities = new DesiredCapabilities

    capabilities.setJavascriptEnabled(isJsEnabled)
    capabilities.setCapability(
      PhantomJSDriverService.PHANTOMJS_EXECUTABLE_PATH_PROPERTY,
      systemProperties.getProperty("webdriver.phantomjs.binary"))

    capabilities.setCapability(PhantomJSDriverService.PHANTOMJS_CLI_ARGS, Array("--ignore-ssl-errors=yes", "--web-security=false", "--ssl-protocol=any"))

    val phantomJs = new PhantomJSDriver(capabilities)
    maximizeWindow(phantomJs.manage().window())
    
    phantomJs
  }
}
