package uk.gov.hmrc.integration.cucumber.stepdefs.vatTradingDetails

import cucumber.api.scala.{EN, ScalaDsl}
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.vatTradingDetails.TradingNamePage

class TradingName extends BasePage{

  Given("""^I am on the Trading name page$"""){ () =>
    go to TradingNamePage
    TradingNamePage.checkBodyTitle()
  }

  When("""^I attempt to continue without providing a trading name"""){ () =>
    clickSaveAndContinue()
  }

  Then("""^I will be prompted to provide a trading name$"""){ () =>
    TradingNamePage.checkTextField()
  }

  And("""^I select the option 'Yes' for the trading name$"""){ () =>
    TradingNamePage.clickProvideTradingName()
  }

  And("""^I select the option 'No' for the trading name$"""){ () =>
    TradingNamePage.clickNoTradingName()
  }

  And("""^I enter a trading name which is (.*)$"""){ (id: String) =>
    TradingNamePage.provideTradingName(id)
  }

  Then("""^I will see an error 'Tell us if the company trades under any other name' on the trading name page$"""){ () =>
    TradingNamePage.checkRadioError()
  }

  Then("""^I will see an error 'Enter a trading name'$"""){ () =>
    TradingNamePage.checkTradingNameEmptyError()
  }

  Then("""^I will see an error 'Enter a valid trading name'$"""){ () =>
    TradingNamePage.checkInvalidError()
  }

  Then("""^I will be presented with the Trading Name page$""") { () =>
    BasePage.waitForPageToBeLoaded(TradingNamePage.checkBodyTitle(), "Failed to load Trading Name Page")
  }

  Given("""^I have provided a '(.*)' trading name for the company$""") { (tradingName: String) =>
    go to TradingNamePage
    TradingNamePage.checkBodyTitle()
    TradingNamePage.clickProvideTradingName()
    TradingNamePage.provideTradingName(tradingName)
  }

}


