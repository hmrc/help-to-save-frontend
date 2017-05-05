package hmrc.utils

import java.io.{FileNotFoundException, IOException, File}
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.Properties

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import org.openqa.selenium.chrome.{ChromeDriver, ChromeOptions}
import org.openqa.selenium.firefox.{FirefoxDriver, FirefoxProfile}
import org.openqa.selenium.htmlunit.HtmlUnitDriver
import org.openqa.selenium.phantomjs.PhantomJSDriver
import org.openqa.selenium.remote.{CapabilityType, DesiredCapabilities, RemoteWebDriver}
import org.openqa.selenium.{Platform, WebDriver}
import play.api.libs.json.Json
import org.openqa.selenium.{Proxy, WebDriver}
import org.openqa.selenium.Proxy.ProxyType
import scala.collection.JavaConversions._
import scala.io.Source
import org.apache.commons.lang3.StringUtils
import org.openqa.selenium.WebDriver

import scala.util.Try


object SingletonDriver extends Driver

class Driver {
  private val SAUCY = "saucy"
  private val ZAP = "zap"

  // set flag to true to see full information about browserstack webdrivers on initialisation
  private val DRIVER_INFO_FLAG = false

  var instance: WebDriver = null
  private var baseWindowHandle: String = null
  var javascriptEnabled: Boolean = true

  def setJavascript(enabled: Boolean) {
    javascriptEnabled = enabled
    if (instance != null) closeInstance()
  }

  def getInstance(): WebDriver = {
    if (instance == null) {
      initialiseBrowser()

      /* Runtime.getRuntime.addShutdownHook(new Thread() {
         @Override
         override def run() {
           instance.close()
         }
       })*/
    }
    instance
  }

  def initialiseBrowser() {
    instance = createBrowser()
    instance.manage().window().maximize()
    baseWindowHandle = instance.getWindowHandle
  }

  def closeInstance() = {
    if (instance != null) {

      closeNewlyOpenedWindows()

      instance.close()
      instance = null
      baseWindowHandle = null
    }
  }

  def closeNewlyOpenedWindows() {
    instance.getWindowHandles.toList.foreach(w =>
      if (w != baseWindowHandle) instance.switchTo().window(w).close()
    )

    instance.switchTo().window(baseWindowHandle)
  }

  private def createBrowser(): WebDriver = {
    def createFirefoxDriver: WebDriver = {
      val profile: FirefoxProfile = new FirefoxProfile
      profile.setPreference("javascript.enabled", javascriptEnabled)
      profile.setAcceptUntrustedCertificates(true)
      new FirefoxDriver(profile)
    }

    lazy val systemProperties = System.getProperties
    lazy val isMac: Boolean = getOs.startsWith("Mac")
    lazy val isLinux: Boolean = getOs.startsWith("Linux")
    lazy val linuxArch = systemProperties.getProperty("os.arch")
    lazy val isWindows: Boolean = getOs.startsWith("Windows")
    def getOs = System.getProperty("os.name")
    def createChromeDriver(): WebDriver = {
      println(s"JAVASCRIPT ENABLED: $javascriptEnabled")
      if (StringUtils.isEmpty(systemProperties.getProperty("webdriver.chrome.driver"))) {
        if (isMac)
          systemProperties.setProperty("webdriver.chrome.driver", "drivers/chromedriver_mac")
        else if (isLinux && linuxArch == "amd32")
          systemProperties.setProperty("webdriver.chrome.driver", "drivers/chromedriver_linux32")
        else if (isWindows)
          System.setProperty("webdriver.chrome.driver", "drivers//chromedriver_win32")
        else
          systemProperties.setProperty("webdriver.chrome.driver", "drivers/chromedriver_linux64")
      }

      val capabilities = DesiredCapabilities.chrome()
      capabilities.setJavascriptEnabled(true)

      val options = new ChromeOptions()
      //    options.addArguments("--load-extension=" + "addons/waveLocal"); // path of your unpacked
//      options.addExtensions(new File("addons/waveLocal.crx"))
      capabilities.setCapability(ChromeOptions.CAPABILITY, options)
      val driver = new ChromeDriver(capabilities)
      driver.manage().window().maximize()
      driver
    }

    def createPhantomJsDriver: WebDriver = {
      val cap = new DesiredCapabilities()
      cap.setJavascriptEnabled(javascriptEnabled)
      new PhantomJSDriver(cap)
    }

    def createHMTLUnitDriver: WebDriver = {
      new HtmlUnitDriver()
    }

    def createSaucyDriver: WebDriver = {
      val capabilities = DesiredCapabilities.firefox()
      capabilities.setCapability("version", "22")
      capabilities.setCapability("platform", "OS X 10.9")

      capabilities.setCapability("name", "Frontend Integration") // TODO: should we add a timestamp here?


      //Need to enquire about this website.
      new RemoteWebDriver(
        new java.net.URL("http://Optimus:3e4f3978-2b40-4965-a6b3-4fb7243bc1f2@ondemand.saucelabs.com:80/wd/hub"), //
        capabilities)
    }

    def createBrowserStackDriver: WebDriver = {

      var userName: String = null
      var automateKey: String = null

      try {
        val prop: Properties = new Properties()
        prop.load(this.getClass().getResourceAsStream("/browserConfig.properties"))

        userName = prop.getProperty("username")
        automateKey = prop.getProperty("automatekey")

      }
      catch{
        case e: FileNotFoundException => e.printStackTrace();
        case e: IOException => e.printStackTrace();
      }

      // create capabilities with device/browser settings from config file
      val bsCaps = getBrowserStackCapabilities
      val desCaps = new DesiredCapabilities(bsCaps)

      // set additional generic capabilities
      desCaps.setCapability("browserstack.debug", "true")
      desCaps.setCapability("browserstack.local", "true")
      desCaps.setCapability("build", "E2E Selenium Tests for Help to Save")


      val bsUrl = s"http://$userName:$automateKey@hub-cloud.browserstack.com/wd/hub"
      val rwd = new RemoteWebDriver(new URL(bsUrl), desCaps)
      printCapabilities(rwd, DRIVER_INFO_FLAG)
      rwd
    }

    def getBrowserStackCapabilities: Map[String, Object] = {
      val testDevice = System.getProperty("testDevice", "BS_Win8_1_IE_11")
      val resourceUrl= s"/browserstackdata/$testDevice.json"
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
      for (key <- basicKeyList)  {
        if (capsMap.containsKey(key)) {
          value = capsMap.get(key)
          println(s"$key : $value")
        } else {
          println(s"$key : not set")

        }
      }

      if (fullDump) {
        // step 3, if requested, dump everything
        println("Full Details >>>>>>")
        for (key <- capsMap.keySet()) {
          value = capsMap.get(key)
          println(s"$key : $value")
        }
      }
    }

    def createZapDriver: WebDriver = {
      val proxy: Proxy = new Proxy()
      proxy.setAutodetect(false)
      proxy.setProxyType(ProxyType.MANUAL)
      proxy.setHttpProxy("localhost:8080")

      val capabilities = DesiredCapabilities.firefox()

      capabilities.setCapability(CapabilityType.PROXY, proxy)

      new FirefoxDriver(capabilities)
    }

    val environmentProperty = System.getProperty("browser","chrome")
    environmentProperty match {
      case "firefox" => createFirefoxDriver
      case "browserstack" => createBrowserStackDriver
      case "htmlunit" => createHMTLUnitDriver
      case "chrome" => createChromeDriver

      case SAUCY => createSaucyDriver
      //case ZAP => createZap
      case _ => throw new IllegalArgumentException(s"Browser type not recognised: -D$environmentProperty")
    }
  }
}


