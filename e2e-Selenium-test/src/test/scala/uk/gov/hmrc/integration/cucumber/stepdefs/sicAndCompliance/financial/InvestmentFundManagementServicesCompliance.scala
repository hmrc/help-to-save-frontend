package uk.gov.hmrc.integration.cucumber.stepdefs.sicAndCompliance.financial

import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.sicAndCompliance.financial.InvestmentFundManagementServicesCompliancePage


class InvestmentFundManagementServicesCompliance extends BasePage {

  When("""^I am presented with the investment fund management service compliance page$"""){ () =>
    go to InvestmentFundManagementServicesCompliancePage
    InvestmentFundManagementServicesCompliancePage.checkBodyTitle
  }

  And("""^I select '(.*)' on the investment fund management service compliance page$"""){ (option: String) =>
    InvestmentFundManagementServicesCompliancePage.clickInvestmentFundManagementServicesOption(option)
  }

  Then("""^I will see an error 'Tell us if the company provides investment fund management services' on the labour compliance page$"""){ () =>
    InvestmentFundManagementServicesCompliancePage.checkRadioError()
  }

  Then("""^I will be presented with the investment fund management service compliance page$"""){ () =>
    InvestmentFundManagementServicesCompliancePage.checkBodyTitle
  }

  Then("""^the selection of '(.*)' I made on the investment fund management service page will be pre-selected$"""){ (option: String) =>
    InvestmentFundManagementServicesCompliancePage.checkInvestmentFundManagementServicesOption(option)
  }
}
