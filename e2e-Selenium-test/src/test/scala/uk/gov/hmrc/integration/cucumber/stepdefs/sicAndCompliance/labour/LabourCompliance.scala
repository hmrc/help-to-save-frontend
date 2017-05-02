package uk.gov.hmrc.integration.cucumber.stepdefs.sicAndCompliance.labour

import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.sicAndCompliance.labour.LabourCompliancePage


class LabourCompliance extends BasePage {

  When("""^I am presented with the labour compliance page$"""){ () =>
    go to LabourCompliancePage
    LabourCompliancePage.checkBodyTitle()
  }

  Then("""^I select '(.*)' on the labour compliance page$"""){ (option: String) =>
    LabourCompliancePage.clickLabourComplianceOption(option)
  }

  Then("""^I will see and error 'Tell us if your company provides workers to other employers' on the labour compliance page$"""){ () =>
    LabourCompliancePage.checkRadioError()
  }

  Then("""^I will be presented with the labour compliance page$"""){ () =>
    LabourCompliancePage.checkBodyTitle()
  }
}
