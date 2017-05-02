package uk.gov.hmrc.integration.cucumber.stepdefs.vatFinancials

import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.vatFinancials.VatChargeExpectancyPage


class VatChargeExpectancy extends BasePage{

  When("""^I am on the vat charge expectancy page$"""){ () =>
    go to VatChargeExpectancyPage
    VatChargeExpectancyPage.checkBodyTitle()
  }

  Then("""^I will be presented with the vat charge expectancy page$"""){ () =>
    VatChargeExpectancyPage.checkBodyTitle()
  }

  Then("""^I select '(.*)' on the 'Do you expect to reclaim VAT...'$"""){ (option: String) =>
    VatChargeExpectancyPage.clickVatChargeOption(option)
  }

  And("""^the selection of '(.*)' I made on the vat charge expectancy page will be pre-selected$"""){ (option: String) =>
    VatChargeExpectancyPage.checkVatChargeOption(option)
  }

  Then("""^I will see an error 'Tell us if you expect to reclaim more VAT than you charge' on the vat charge expectancy page$"""){ () =>
    VatChargeExpectancyPage.checkRadioError()
  }
}

