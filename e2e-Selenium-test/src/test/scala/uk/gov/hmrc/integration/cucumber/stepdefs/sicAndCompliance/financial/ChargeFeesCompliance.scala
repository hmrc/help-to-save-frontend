package uk.gov.hmrc.integration.cucumber.stepdefs.sicAndCompliance.financial

import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.sicAndCompliance.financial.ChargeFeesCompliancePage


class ChargeFeesCompliance extends BasePage {

  When("""^I am presented with the charge fees compliance page$"""){ () =>
    go to ChargeFeesCompliancePage
    ChargeFeesCompliancePage.checkBodyTitle()
  }

  And("""^I select (.*) in charge fees compliance page$"""){ (option: String) =>
    ChargeFeesCompliancePage.clickChargeFeesOption(option)
  }

  Then("""^I will see an error 'Tell us if the company charges fees for introducing clients to financial service providers' on the skilled workers page$"""){ () =>
    ChargeFeesCompliancePage.checkRadioError()
  }

  Then("""^I will be presented with the charge fees compliance page$"""){ () =>
    ChargeFeesCompliancePage.checkBodyTitle()
  }

  Then("""^the selection of '(.*)' I made on the charge fees compliance page will be pre-selected$"""){ (option: String) =>
    ChargeFeesCompliancePage.checkChargeFeesOption(option)
  }
}
