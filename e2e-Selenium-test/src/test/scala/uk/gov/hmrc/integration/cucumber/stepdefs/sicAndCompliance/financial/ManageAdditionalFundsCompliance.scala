package uk.gov.hmrc.integration.cucumber.stepdefs.sicAndCompliance.financial

import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.sicAndCompliance.financial.ManageAdditionalFundsCompliancePage


class ManageAdditionalFundsCompliance extends BasePage {

  When("""^I am presented with the manage additional funds compliance page$"""){ () =>
    go to ManageAdditionalFundsCompliancePage
    ManageAdditionalFundsCompliancePage.checkBodyTitle()
  }

  And("""^I select (.*) in manage additional funds compliance page$"""){ (option: String) =>
    ManageAdditionalFundsCompliancePage.clickManageAdditionalFundsOption(option)
  }

  Then("""^I will see an error 'Tell us if the company manages any funds that are not included in this list' on the skilled workers page$"""){ () =>
    ManageAdditionalFundsCompliancePage.checkRadioError()
  }

  Then("""^I will be presented with the manage funds additional compliance page$"""){ () =>
    ManageAdditionalFundsCompliancePage.checkBodyTitle()
  }

  Then("""^the selection of '(.*)' I made on the manage additional funds page will be pre-selected$"""){ (option: String) =>
    ManageAdditionalFundsCompliancePage.checkManageAdditionalFundsOption(option)
  }
}
