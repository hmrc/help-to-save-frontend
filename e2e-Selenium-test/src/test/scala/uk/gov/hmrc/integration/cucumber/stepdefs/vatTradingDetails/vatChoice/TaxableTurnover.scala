package uk.gov.hmrc.integration.cucumber.stepdefs.vatTradingDetails.vatChoice

import cucumber.api.scala.{EN, ScalaDsl}
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.vatTradingDetails.vatChoice.TaxableTurnoverPage

class TaxableTurnover extends BasePage {

  Given("""^I am on the taxable turnover page$"""){ () =>
    go to TaxableTurnoverPage
  }

  Then("""^I will be presented with the >83K question$"""){ () =>
    BasePage.waitForPageToBeLoaded(TaxableTurnoverPage.checkBodyTitle(), "Failed to load Taxable Turnover Page")
  }

  Then("""^I will see an error "Tell us if you expect the company's turnover to be more than £83,000 in the 30 days after it's registered" on the taxable turnover page$"""){ () =>
    TaxableTurnoverPage.checkRadioError()
  }

  And("""^I select the option '(.*)' for the taxable turnover$"""){ (option: String) =>
    TaxableTurnoverPage.clickTaxableOption(option)
  }

  And("""^the selection of '(.*)' I made previously will be shown$"""){ (option: String) =>
    TaxableTurnoverPage.checkTaxableOption(option)
  }

}
