package uk.gov.hmrc.integration.cucumber.stepdefs.sicAndCompliance.labour

import cucumber.api.scala.{EN, ScalaDsl}
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.sicAndCompliance.labour.TemporaryContractsCompliancePage


class TemporaryContractsCompliance extends BasePage {

  When("""^I am presented with the temporary contracts compliance page$"""){ () =>
    go to TemporaryContractsCompliancePage
    TemporaryContractsCompliancePage.checkBodyTitle()
  }

  Then("""^I select '(.*)' on the temporary contracts compliance page$"""){ (option: String) =>
    TemporaryContractsCompliancePage.clickTemporaryContractsComplianceOption(option)
  }

  Then("""^I will see an error 'Tell us if your company provides workers on temporary contracts' on the labour compliance page$"""){ () =>
    TemporaryContractsCompliancePage.checkRadioError()
  }

  Then("""^I will be presented with the temporary contracts compliance page$"""){ () =>
    TemporaryContractsCompliancePage.checkBodyTitle()
  }
}
