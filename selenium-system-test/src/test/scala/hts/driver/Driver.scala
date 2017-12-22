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

package hts.driver

import java.io.{FileNotFoundException, IOException}
import java.net.URL
import java.util.Properties
import java.util.concurrent.TimeUnit

import cats.instances.string._
import cats.syntax.eq._
import cats.syntax.either._
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.{ChromeDriver, ChromeOptions}
import org.openqa.selenium.remote.{DesiredCapabilities, RemoteWebDriver}

import scala.collection.JavaConversions._
import scala.io.Source

object Driver {

  var sessionID = ""

  // set flag to true to see full information about browserstack webdrivers on initialisation
  private val DRIVER_INFO_FLAG = false

  private val systemProperties = System.getProperties

  def newWebDriver(): Either[String, WebDriver] = {
    val selectedDriver: Either[String, WebDriver] = Option(systemProperties.getProperty("browser")).map(_.toLowerCase) match {
      case Some("chrome")       ⇒ Right(createChromeDriver(false))
      case Some("zap-chrome")   ⇒ Right(createZapChromeDriver())
      case Some("headless")     ⇒ Right(createChromeDriver(true))
      case Some("browserstack") ⇒ Right(createBrowserStackDriver)
      case Some(other)          ⇒ Left(s"Unrecognised browser: $other")
      case None                 ⇒ Left("No browser set")
    }

    selectedDriver.foreach { driver ⇒
      val (_, _) = (sys.addShutdownHook(driver.quit()),
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
    setChromeDriver()

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
    setChromeDriver()

    val capabilities = DesiredCapabilities.chrome()
    val options = new ChromeOptions()
    options.addArguments("test-type")
    options.addArguments("--proxy-server=http://localhost:11000")
    capabilities.setCapability(ChromeOptions.CAPABILITY, options)
    val driver = new ChromeDriver(capabilities)
    val caps = driver.getCapabilities
    driver
  }

  def createBrowserStackDriver: WebDriver = {

    var userName: String = null
    var automateKey: String = null

    try {
      val prop: Properties = new Properties()
      prop.load(this.getClass().getResourceAsStream("/browserConfig.properties"))

      userName = prop.getProperty("username")
      automateKey = prop.getProperty("automatekey")

    } catch {
      case e: FileNotFoundException ⇒ e.printStackTrace();
      case e: IOException           ⇒ e.printStackTrace();
    }

    // create capabilities with device/browser settings from config file
    val bsCaps = getBrowserStackCapabilities
    val desCaps = DesiredCapabilities.chrome
    val driver = new ChromeDriver(desCaps)
    val caps = driver.getCapabilities
    driver

    // set additional generic capabilities
    desCaps.setCapability("browserstack.debug", "true")
    desCaps.setCapability("browserstack.local", "true")
    desCaps.setCapability("project", "HTS")
    desCaps.setCapability("build", "Help to Save Frontend")

    val bsUrl = s"http://$userName:$automateKey@hub-cloud.browserstack.com/wd/hub"
    val rwd = new RemoteWebDriver(new URL(bsUrl), desCaps)
    val sessionId = rwd.getSessionId.toString
    sessionID = sessionId
    printCapabilities(rwd, DRIVER_INFO_FLAG)
    rwd
  }

  def getBrowserStackCapabilities: Map[String, Object] = {
    val testDevice = System.getProperty("testDevice", "BS_Win10_Chrome_55")
    val resourceUrl = s"/browserstackdata/$testDevice.json"
    val cfgJsonString = Source.fromURL(getClass.getResource(resourceUrl)).mkString
    val mapper = new ObjectMapper() with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)
    mapper.readValue[Map[String, Object]](cfgJsonString)
  }

  def printCapabilities(rwd: RemoteWebDriver, fullDump: Boolean): Unit = {
    var key = ""
    var value: Any = null

    println("RemoteWebDriver Basic Capabilities >>>>>>")
    // step 1, print out the common caps which have getters
    val caps = rwd.getCapabilities
    val platform = caps.getPlatform
    println(s"platform : $platform")
    val browserName = caps.getBrowserName
    println(s"browserName : $browserName")
    val version = caps.getVersion
    println(s"version : $version")

    // step 2, print out common caps which need to be explicitly retrieved using their key
    val capsMap = caps.asMap()
    val basicKeyList = List("os", "os_version", "mobile", "device", "deviceName")
    for (key ← basicKeyList) {
      if (capsMap.containsKey(key)) {
        value = capsMap.get(key)
        println(s"$key : $value")
      } else {
        println(s"$key : not set")

      }
    }
    //    if (fullDump) {
    //      // step 3, if requested, dump everything
    //      println("Full Details >>>>>>")
    //      for (key ← capsMap.keySet()) {
    //        value = capsMap.get(key)
    //        println(s"$key : $value")
    //      }
    //    }
  }
}
