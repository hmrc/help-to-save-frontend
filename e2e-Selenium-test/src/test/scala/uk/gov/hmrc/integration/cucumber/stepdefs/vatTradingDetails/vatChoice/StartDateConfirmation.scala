package uk.gov.hmrc.integration.cucumber.stepdefs.vatTradingDetails.vatChoice

import cucumber.api.scala.{EN, ScalaDsl}
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.vatTradingDetails.vatChoice.StartDateConfirmationPage


class StartDateConfirmation extends BasePage {

  Then("""^I will be presented with the Start Date Confirmation page$"""){ () =>
    StartDateConfirmationPage.checkBodyTitle()
  }

  Then("""^I am on the Start Date Confirmation page$"""){ () =>
    go to StartDateConfirmationPage
    StartDateConfirmationPage.checkBodyTitle()
  }
}
