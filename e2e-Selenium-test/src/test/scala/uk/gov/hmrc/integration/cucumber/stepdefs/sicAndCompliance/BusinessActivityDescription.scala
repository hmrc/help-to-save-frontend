package uk.gov.hmrc.integration.cucumber.stepdefs.sicAndCompliance

import cucumber.api.scala.{EN, ScalaDsl}
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.sicAndCompliance.BusinessActivityDescriptionPage

class BusinessActivityDescription extends BasePage {

  And("""^I am presented with the business activity description$""") { () =>
    go to BusinessActivityDescriptionPage
    BusinessActivityDescriptionPage.checkBodyTitle()
  }

  And("""^I provide the '(.*)' business activity description$""") { (validDescription: String) =>
    BusinessActivityDescriptionPage.provideBusinessDescription(validDescription)
  }

  Then("""^I will see an error 'Tell us what the company does' in business activity description page$""") { () =>
    BusinessActivityDescriptionPage.checkEmptyDescriptionError()
  }

  When("""^I provide 'invalid' description$""") { (invalidDescription: String) =>
    BusinessActivityDescriptionPage.provideBusinessDescription(invalidDescription)
  }

  Then("""^I will see an error 'Enter a valid business activity description' in business activity description page$""") { () =>
    BusinessActivityDescriptionPage.checkInvalidDescriptionError()
  }

  Then("""^I will be presented with the business activity description page$""") { () =>
    BusinessActivityDescriptionPage.checkBodyTitle()
  }

  And("""^The business activity description will be pre-populated$""") { () =>
    BusinessActivityDescriptionPage.checkBusinessDescription()
  }
}
