package uk.gov.hmrc.integration.cucumber.stepdefs.sicAndCompliance.financial

import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.sicAndCompliance.financial.DiscretionaryInvestmentManagementServicesCompliancePage

class DiscretionaryInvestmentManagementServicesCompliance extends BasePage {

  When("""^I am presented with the discretionary investment management service compliance page$"""){ () =>
    go to DiscretionaryInvestmentManagementServicesCompliancePage
    DiscretionaryInvestmentManagementServicesCompliancePage.checkBodyTitle
  }

  And("""^I select '(.*)' on the discretionary investment management service compliance page$"""){ (option: String) =>
    DiscretionaryInvestmentManagementServicesCompliancePage.clickDiscretionaryInvestmentManagementServicesOption(option)
  }

  Then("""^I will see an error 'Tell us if the company provides discretionary investment management services, or introduces clients to companies who do' on the labour compliance page$"""){ () =>
    DiscretionaryInvestmentManagementServicesCompliancePage.checkRadioError()
  }

  Then("""^I will be presented with the discretionary investment management service compliance page$"""){ () =>
    DiscretionaryInvestmentManagementServicesCompliancePage.checkBodyTitle
  }

  Then("""^the selection of '(.*)' I made on the discretionary investment management compliance service page will be pre-selected$"""){ (option: String) =>
    DiscretionaryInvestmentManagementServicesCompliancePage.checkDiscretionaryInvestmentManagementServicesOption(option)
  }
}
