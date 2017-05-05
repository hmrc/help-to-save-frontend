package hts.driver

import org.apache.commons.lang3.StringUtils
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.{WebDriverException, WebDriver}
import org.openqa.selenium.firefox.MarionetteDriver
import org.openqa.selenium.phantomjs.{PhantomJSDriverService, PhantomJSDriver}
import org.openqa.selenium.remote.{BrowserType, DesiredCapabilities}
import org.scalatest.selenium.{Chrome, Firefox}
import org.openqa.selenium.chrome.ChromeDriver
import java.util.concurrent.TimeUnit

object Browser extends StartUpTearDown {
  def getOs = System.getProperty("os.name")

  lazy val systemProperties = System.getProperties
  lazy val isMac: Boolean = getOs.startsWith("Mac")
  lazy val isLinux: Boolean = getOs.startsWith("Linux")
  lazy val linuxArch = systemProperties.getProperty("os.arch")
  override val driver: WebDriver = null
  var jsStatus = true

  object PhantomJSDriverObject {
    def apply(capabilities: DesiredCapabilities) = new PhantomJSDriver(capabilities)
  }

  def javascriptEnabled: Boolean = {
    if (!StringUtils.isEmpty(systemProperties.getProperty("javascriptEnabled")))
      true
    else
      false
  }

  def createGeckoDriver(): WebDriver = {
    if (StringUtils.isEmpty(systemProperties.getProperty("webdriver.gecko.driver"))) {
      if (isMac)
        systemProperties.setProperty("webdriver.gecko.driver", "drivers/geckodriver_mac")
      else if (isLinux)
        systemProperties.setProperty("webdriver.gecko.driver", "drivers/geckodriver_linux64")
      else
        systemProperties.setProperty("webdriver.gecko.driver", "drivers/geckodriver.exe")
    }
    val capabilities = DesiredCapabilities.firefox()
    capabilities.setJavascriptEnabled(!isJsDisabled)
    val driver = new MarionetteDriver()
    try {
      driver.manage.window.maximize()
    }
    catch {
      case e: WebDriverException => //Swallow exception
    }
    driver
  }

  def createFirefoxDriver(): WebDriver = {
    jsStatus = true
    val capabilities = DesiredCapabilities.firefox()
    capabilities.setJavascriptEnabled(jsStatus)
    val driver = Firefox.webDriver
    capabilities.setBrowserName(BrowserType.FIREFOX)
    try {
      driver.manage.window.maximize()
    }
    catch {
      case e: WebDriverException => //Swallow exception
    }
    driver
  }

  def createChromeDriver(): WebDriver = {
    if (StringUtils.isEmpty(systemProperties.getProperty("webdriver.chrome.driver"))) {
      if (isMac)
        systemProperties.setProperty("webdriver.chrome.driver", "drivers/chromedriver_mac")
      else if (isLinux && linuxArch == "amd32")
        systemProperties.setProperty("webdriver.chrome.driver", "drivers/chromedriver_linux32")
      else if (isLinux)
        systemProperties.setProperty("webdriver.chrome.driver", "drivers/chromedriver")
      else
        systemProperties.setProperty("webdriver.chrome.driver", "drivers/chromedriver.exe")
    }
    val capabilities = DesiredCapabilities.chrome()
    val options = new ChromeOptions()

//    options.addArguments("--ignore-certificate-errors")
    options.addArguments("test-type")
    options.addArguments("--disable-gpu")
    if (isJsDisabled)
      jsStatus = false
    else
      jsStatus = true
    capabilities.setJavascriptEnabled(jsStatus)
    capabilities.setCapability(ChromeOptions.CAPABILITY, options)
    val driver = Chrome.webDriver
    try {
      driver.manage.window.maximize()
    }
    catch {
      case e: WebDriverException => //Swallow exception
    }

//    System.setProperty("webdriver.chrome.driver", "drivers/chromedriver")
//    val driver:ChromeDriver  = new ChromeDriver
//    driver.manage().timeouts().implicitlyWait(20, TimeUnit.SECONDS)
//    driver.manage().window().maximize()
    driver
  }

  def createPhantomJsDriver(): WebDriver = {
    if (StringUtils.isEmpty(systemProperties.getProperty("webdriver.phantomjs.binary"))) {
      if (isMac)
        systemProperties.setProperty("webdriver.phantomjs.binary", "drivers/phantomjs")
      else if (isLinux && linuxArch == "amd32")
        systemProperties.setProperty("webdriver.phantomjs.binary", "drivers/phantomjs_linux32")
      else
        systemProperties.setProperty("webdriver.phantomjs.binary", "drivers/phantomjs_linux64")
    }

    val capabilities = new DesiredCapabilities
    if (isJsDisabled)
      jsStatus = false
    else
      jsStatus = true
    capabilities.setJavascriptEnabled(jsStatus)
    capabilities.setCapability(
      PhantomJSDriverService.PHANTOMJS_EXECUTABLE_PATH_PROPERTY,
      systemProperties.getProperty("webdriver.phantomjs.binary"))

    capabilities.setCapability(PhantomJSDriverService.PHANTOMJS_CLI_ARGS, Array("--ignore-ssl-errors=yes", "--web-security=false", "--ssl-protocol=any"))

    val phantomJs = PhantomJSDriverObject(capabilities)
    phantomJs.manage().window().maximize()

    phantomJs
  }
}
