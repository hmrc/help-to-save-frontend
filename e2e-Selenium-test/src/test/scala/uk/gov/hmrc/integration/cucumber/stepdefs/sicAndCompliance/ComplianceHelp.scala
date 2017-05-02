package uk.gov.hmrc.integration.cucumber.stepdefs.sicAndCompliance

import cucumber.api.scala.{EN, ScalaDsl}
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.sicAndCompliance.ComplianceHelpPage


class ComplianceHelp extends BasePage {

  Then("""^I will be informed that they are going to have to answer some additional questions$""") { () =>
    ComplianceHelpPage.checkBodyTitle()
  }
}
