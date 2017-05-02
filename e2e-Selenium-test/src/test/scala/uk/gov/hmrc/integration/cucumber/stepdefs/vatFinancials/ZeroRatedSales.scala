package uk.gov.hmrc.integration.cucumber.stepdefs.vatFinancials

import cucumber.api.scala.{EN, ScalaDsl}
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.vatFinancials.ZeroRatedSalesPage


class ZeroRatedSales extends BasePage  {

  Given("""^I am presented with the 'Do you expect to make...' page$"""){ () =>
    go to ZeroRatedSalesPage
    ZeroRatedSalesPage.checkBodyTitle()
  }

  When("""^I select '(.*)' on the zero rated sales page$"""){ (option: String) =>
    ZeroRatedSalesPage.clickZeroRatedSalesOption(option)
  }

  Then("""^I will see an error 'Select an option' on the zero-rated sales page$"""){ () =>
    ZeroRatedSalesPage.checkRadioError()
  }

  Then("""^I will be presented with the zero rated sales page$"""){ () =>
    ZeroRatedSalesPage.checkBodyTitle()
  }

  Then("""^the selection of '(.*)' I made on the zero-rated sales page will be pre-selected$"""){ (option: String) =>
    ZeroRatedSalesPage.checkZeroRatedSalesOption(option)
  }

  And("""^I will see an error 'Tell us if you expect to sell any zero-rated items in the next 12 months' on the zero-rated sales page$""") { () =>
    ZeroRatedSalesPage.checkRadioError()

  }
}
