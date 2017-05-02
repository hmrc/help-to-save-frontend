package uk.gov.hmrc.integration.cucumber.stepdefs.vatFinancials.vatAccountingPeriod

import cucumber.api.scala.{EN, ScalaDsl}
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.vatFinancials.vatAccountingPeriod.VatReturnFrequencyPage


class VatReturnFrequency extends BasePage {


  When("""^I am presented with the vat return frequency page$"""){ () =>
    go to VatReturnFrequencyPage
    VatReturnFrequencyPage.checkBodyTitle()
  }

  And("""^I indicate I would like to submit (.*) VAT returns$"""){ (option: String) =>
    VatReturnFrequencyPage.clickVatReturnFrequency(option)
  }

  Then("""^I will see an error 'Tell us how often you want to submit VAT Returns' on the vat return frequency page$"""){ () =>
    VatReturnFrequencyPage.errorMessage()
  }

  Then("""^I will be presented with the vat return frequency page$"""){ () =>
    VatReturnFrequencyPage.checkBodyTitle()
  }

  And("""^then selection of (.*) I made on the vat charge expectancy page will be pre-selected$"""){ (option: String) =>
    VatReturnFrequencyPage.checkVatReturnFrequency(option)
  }
}
