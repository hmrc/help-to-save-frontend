package uk.gov.hmrc.integration.cucumber.stepdefs.sicAndCompliance.labour

import cucumber.api.scala.{EN, ScalaDsl}
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.sicAndCompliance.labour.SkilledWorkersCompliancePage


class SkilledWorkersCompliance extends BasePage {


  Then("""^I am presented with the skilled workers compliance page$""") { () =>
    go to SkilledWorkersCompliancePage
    SkilledWorkersCompliancePage.checkBodyTitle()
  }

  When("""^I select (.*) in skilled workers compliance page$""") { (skilledWorkersOption: String) =>
    SkilledWorkersCompliancePage.clickSkilledWorkersOption(skilledWorkersOption)
  }

  Then("""^I will see an error 'Tell us if your company provides skilled workers' on the skilled workers page$""") { () =>
    SkilledWorkersCompliancePage.skilledWorkersRadioError()
  }

  Then("""^I will be presented with the skilled workers compliance page$""") { () =>
    SkilledWorkersCompliancePage.checkBodyTitle()
  }

}
