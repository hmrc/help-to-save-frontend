package uk.gov.hmrc.integration.cucumber.stepdefs.sicAndCompliance.financial

import cucumber.api.scala.{EN, ScalaDsl}
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.sicAndCompliance.financial.IntermediaryPage


class Intermediary extends ScalaDsl with EN with BasePage {

  When("""^I am presented with the intermediary page$"""){ () =>
    go to IntermediaryPage
    IntermediaryPage.checkBodyTitle()
  }

  And("""^I select '(.*)' on the intermediary page$"""){ (radioOption: String) =>
    IntermediaryPage.clickIntermediaryRadio(radioOption)
  }

  Then("""^I will see an error 'Tell us if the company provides intermediary services' on the intermediary page$"""){ () =>
    IntermediaryPage.checkIntermediaryRadioError()
  }

  Then("""^I will be presented with the intermediary page$"""){ () =>
    IntermediaryPage.checkBodyTitle()
  }

  Then("""^the selection of '(.*)' I made on the intermediary page will be pre-selected$"""){ (answer: String) =>
    IntermediaryPage.checkIntermediaryRadioOption(answer)
  }

}
