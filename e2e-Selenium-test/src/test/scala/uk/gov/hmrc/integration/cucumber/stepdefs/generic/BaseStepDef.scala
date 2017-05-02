package uk.gov.hmrc.integration.cucumber.stepdefs.generic

import java.util.Calendar

import cucumber.api.java.{After, Before}
import cucumber.api.scala.{EN, ScalaDsl}
import cucumber.api.{DataTable, Scenario}
import org.openqa.selenium._
import uk.gov.hmrc.integration.cucumber.flows.LoginUsingGG
import uk.gov.hmrc.integration.cucumber.pages.generic.{BasePage, TestSetupPage}

class BaseStepDef extends BasePage {

  @Before(Array("@AuthenticatedLogIn"))
  def AuthenticatedLogIn(): Unit = {
    LoginUsingGG.login("authenticated")
  }
  @Before(Array("@UnauthenticatedLogIn"))
  def UnauthenticatedLogIn(): Unit = {
    LoginUsingGG.login("unauthenticated")
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
  }

  Then("""^I continue$"""){ () =>
    clickContinue()
  }

  And("""^I save and continue$""") { () =>
    clickSaveAndContinue()
  }

  And("""^I attempt to continue without making a selection$"""){ () =>
    clickSaveAndContinue()
  }

  Then("""^I prime the application$"""){ (data: DataTable) =>
    go to TestSetupPage
    BasePage.waitForPageToBeLoaded(TestSetupPage.checkBodyTitle(), "Failed to load Test Setup Page")
    TestSetupPage.enterData(data)
    submit()
    BasePage.waitForPageToBeLoaded(TestSetupPage.checkBodyPreText(), "Failed to submit Test Setup Page")
  }

}
