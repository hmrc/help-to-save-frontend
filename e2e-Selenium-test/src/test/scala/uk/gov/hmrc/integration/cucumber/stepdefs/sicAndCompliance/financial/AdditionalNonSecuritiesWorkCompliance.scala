package uk.gov.hmrc.integration.cucumber.stepdefs.sicAndCompliance.financial

import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.sicAndCompliance.financial.AdditionalNonSecuritiesWorkCompliancePage

class AdditionalNonSecuritiesWorkCompliance extends BasePage {

  When("""^I am presented with the additional non securities work compliance page$""") { () =>
    go to AdditionalNonSecuritiesWorkCompliancePage
    AdditionalNonSecuritiesWorkCompliancePage.checkBodyTitle
  }

  And("""^I select '(.*)' on the additional non securities work compliance page$""") { (option: String) =>
    AdditionalNonSecuritiesWorkCompliancePage.clickAdditionalNonSecuritiesWorkOption(option)
  }

  Then("""^I will see an error 'Tell us if the company does additional work \(excluding securities\) when introducing a client to a financial service provider' on the labour compliance page$""") { () =>
    AdditionalNonSecuritiesWorkCompliancePage.checkRadioError()
  }

  Then("""^I will be presented with the additional non securities work compliance page$""") { () =>
    AdditionalNonSecuritiesWorkCompliancePage.checkBodyTitle
  }

  Then("""^the selection of '(.*)' I made on the additional non securities work compliance page will be pre-selected$""") { (option: String) =>
    AdditionalNonSecuritiesWorkCompliancePage.checkAdditionalNonSecuritiesWorkOption(option)
  }
}
