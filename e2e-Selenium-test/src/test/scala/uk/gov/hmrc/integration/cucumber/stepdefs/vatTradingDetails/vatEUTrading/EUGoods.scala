package uk.gov.hmrc.integration.cucumber.stepdefs.vatTradingDetails.vatEUTrading

import cucumber.api.scala.{EN, ScalaDsl}
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.vatTradingDetails.vatEUTrading.EUGoodsPage

class EUGoods extends BasePage{

  When("""^I am presented with the EU goods page$""") { () =>
    go to EUGoodsPage
    EUGoodsPage.checkBodyTitle()
  }

  And("""^I select the option '(.*)' for the EU Goods$""") { (option: String) =>
    EUGoodsPage.clickEUGoodsOption(option)
  }

  Then("""^I will see an error "Tell us if you import or export goods from or to countries outside the EU" on the EU Goods page$""") { () =>
    EUGoodsPage.checkRadioError()
  }

  Then("""^I will be presented with the EU goods page$""") { () =>
    EUGoodsPage.checkBodyTitle()
  }

  Then("""^the value I entered for the '(.*)' will be pre-selected in EU goods page$"""){ (option: String) =>
    EUGoodsPage.checkEUGoodsOption(option)
  }

  And("""^I provide a different option as '(.*)' in EU goods page$"""){ (option: String) =>
    EUGoodsPage.clickEUGoodsOption(option)
  }
}
