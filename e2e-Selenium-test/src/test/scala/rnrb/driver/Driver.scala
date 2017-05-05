package hts.driver

import java.util.concurrent.TimeUnit

import org.apache.commons.lang3.StringUtils
import org.openqa.selenium.WebDriver
import hts.driver.Browser._

import scala.util.Try

object Driver extends Driver

class Driver {
  val systemProperties = System.getProperties

  val webDriver: WebDriver = {
    sys addShutdownHook {
      Try(webDriver.quit())
    }

    val selectedDriver = if (!StringUtils.isEmpty(System.getProperty("browser"))) {
      val targetBrowser = systemProperties.getProperty("browser").toLowerCase
      targetBrowser match {

        case "firefox"                  => createFirefoxDriver()
        case "chrome"                   => createChromeDriver()
        case "phantomjs"                => createPhantomJsDriver()
        case "gecko"                    => createGeckoDriver()
        case _                          => throw new IllegalArgumentException(s"Browser type not recognised")
      }
    }
    else {
      createFirefoxDriver()
    }
    selectedDriver.manage().timeouts().implicitlyWait(0, TimeUnit.SECONDS)
    selectedDriver
  }
}
