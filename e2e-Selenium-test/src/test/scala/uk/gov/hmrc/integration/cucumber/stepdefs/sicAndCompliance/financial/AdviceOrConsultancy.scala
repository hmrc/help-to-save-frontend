package uk.gov.hmrc.integration.cucumber.stepdefs.sicAndCompliance.financial

import cucumber.api.scala.{EN, ScalaDsl}
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.sicAndCompliance.financial.AdviceOrConsultancyPage

class AdviceOrConsultancy extends ScalaDsl with EN with BasePage {

  When("""^I am presented with the advice or consultancy page$"""){ () =>
    go to AdviceOrConsultancyPage
    AdviceOrConsultancyPage.checkBodyTitle()
  }

  And("""^I select '(.*)' on the advice or consultancy page$""") { (option: String) =>
    AdviceOrConsultancyPage.clickAdviceOrConsultancyOption(option)
  }

  Then("""^I will see an error 'Tell us if the company provides advice-only or consultancy services' on the advice or consultancy page$"""){ () =>
    AdviceOrConsultancyPage.checkAdviceOrConsultancyRadioError()
  }

  Then("""^I will be presented with the advice or consultancy page$"""){ () =>
    AdviceOrConsultancyPage.checkBodyTitle()
  }

  Then("""^the selection of '(.*)' I made on the advice or consultancy page will be pre-selected$"""){ (option: String) =>
    AdviceOrConsultancyPage.checkAdviceOrConsultancyOption(option)
  }

}
