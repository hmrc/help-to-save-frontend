package hmrc.steps

import java.util.Calendar

import cucumber.api.java.{After, Before}
import cucumber.api.scala.{EN, ScalaDsl}
import cucumber.api.{DataTable, Scenario}
import org.openqa.selenium._
import hmrc.flows.LoginUsingGG
import hmrc.pages.BasePage
import hmrc.utils.SingletonDriver

class BaseSteps extends BasePage {

  @Before
  def setUpDriver: Unit = {
    driver = SingletonDriver.getInstance()
  }

  @After
  def screenshots(result: Scenario) {
    if (result.isFailed) {
        if (driver.isInstanceOf[TakesScreenshot]) {
          try {
            println(s"******************* Test - FAILED *******************")
            println(s"***************** Taking screenshot *****************")
            setCaptureDir("target/screenshots")
            capture to result.getId + Calendar.getInstance().getTime
          } catch {
            case somePlatformsDontSupportScreenshots: WebDriverException => System.err.println(somePlatformsDontSupportScreenshots.getMessage)
          }
        }
    }
    driver.quit()
  }
}
