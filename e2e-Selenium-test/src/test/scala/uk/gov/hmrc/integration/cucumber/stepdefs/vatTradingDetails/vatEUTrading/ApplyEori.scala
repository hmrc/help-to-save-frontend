package uk.gov.hmrc.integration.cucumber.stepdefs.vatTradingDetails.vatEUTrading

import cucumber.api.scala.{EN, ScalaDsl}
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.vatTradingDetails.vatEUTrading.ApplyEoriPage


class ApplyEori extends BasePage {

  When("""^I am presented with the apply eori page$""") { () =>
    go to ApplyEoriPage
    ApplyEoriPage.checkBodyTitle()
  }

  And("""^I select '(.*)' in the apply eori page$""") { (option: String) =>
    ApplyEoriPage.clickEconomicOperatorOption(option)
  }

  Then("""^I will see an error 'Tell us if you want to apply for an Economic Operator Registration and Identification \(EORI\) number' on the economic operator page$""") { () =>
    ApplyEoriPage.checkRadioError()
  }

  Then("""^I will be presented with the apply eori page$""") { () =>
    ApplyEoriPage.checkBodyTitle()
  }

  Then("""^the value I entered for the (.*) will be selected$""") { (eoriValue: String) =>
    ApplyEoriPage.checkEconomicOperatorOption(eoriValue)
  }

  And("""^I provide a different apply as '(.*)' in apply eori page$"""){ (option: String) =>
    ApplyEoriPage.changeEconomicOperatorOption(option)
  }

  Then("""^the value I entered for the '(.*)' will be pre-selected in apply eori page$"""){ (eoriValue: String) =>
    ApplyEoriPage.verifyEconomicOperatorOption(eoriValue)
  }

}
