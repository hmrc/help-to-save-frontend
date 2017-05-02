package uk.gov.hmrc.integration.cucumber.stepdefs.generic

import java.util.Calendar

import cucumber.api.java.{After, Before}
import cucumber.api.scala.{EN, ScalaDsl}
import cucumber.api.{DataTable, Scenario}
import org.openqa.selenium._
import uk.gov.hmrc.integration.cucumber.flows.LoginUsingGG
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage

class BaseStepDef extends BasePage {

//  @Before(Array("@AuthenticatedLogIn"))
//  def AuthenticatedLogIn(): Unit = {
//    LoginUsingGG.login("authenticated")
//  }
//  @Before(Array("@UnauthenticatedLogIn"))
//  def UnauthenticatedLogIn(): Unit = {
//    LoginUsingGG.login("unauthenticated")
//  }

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
