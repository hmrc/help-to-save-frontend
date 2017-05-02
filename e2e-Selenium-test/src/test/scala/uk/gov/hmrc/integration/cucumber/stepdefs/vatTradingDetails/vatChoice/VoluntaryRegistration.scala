package uk.gov.hmrc.integration.cucumber.stepdefs.vatTradingDetails.vatChoice

import cucumber.api.scala.{EN, ScalaDsl}
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.vatTradingDetails.vatChoice.VoluntaryRegistrationPage


class VoluntaryRegistration extends BasePage{

  Then("""^I will be presented with the 'Do you want to register voluntarily for VAT' page$""") { () =>
    VoluntaryRegistrationPage.checkBodyTitle()
  }

  Then("""^I will see an error 'Tell us if you want to register voluntarily for VAT' on the voluntary register page$""") { () =>
    VoluntaryRegistrationPage.errorMessage()
  }

  And("""^I select the option 'Yes' for the voluntary registration$"""){ () =>
    VoluntaryRegistrationPage.clickRegisterVoluntarily()
  }

  And("""^I select the option 'No' for the voluntary registration$"""){ () =>
    VoluntaryRegistrationPage.clickNotToRegisterVoluntarily()
  }

  Given("""^I am on the voluntary registration page$""") { () =>
    go to VoluntaryRegistrationPage
    VoluntaryRegistrationPage.checkBodyTitle()
  }

  Then("""^I will be presented with the voluntary-registration page$"""){ () =>
    BasePage.waitForPageToBeLoaded(VoluntaryRegistrationPage.checkBodyTitle(), "Failed to load Voluntary Registration Page")
  }
}
