package uk.gov.hmrc.integration.cucumber.stepdefs.vatFinancials

import cucumber.api.scala.{EN, ScalaDsl}
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.vatFinancials.EstimateZeroRatedSalesPage


class EstimateZeroRatedSales extends BasePage {

  Then("""^I am presented with the 'Estimated zero rated...' page$"""){ () =>
    go to EstimateZeroRatedSalesPage
  }

  And("""^I enter a valid value and I click 'Save and continue' on the estimated zero-rated sales page$"""){ () =>
    EstimateZeroRatedSalesPage.estimatedZeroRatedSales()
    clickSaveAndContinue()
  }

  When("""^I attempt to continue without entering a value on the estimate zero-rated sales page$"""){ () =>
    clickSaveAndContinue()
  }

  Then("""^I will be presented with the estimate zero-rated sales page"""){ () =>
    EstimateZeroRatedSalesPage.checkBodyTitle()
  }

  Then("""^when I enter a value which is (.*) on the estimate zero-rated sales page$"""){ (amount: String) =>
    EstimateZeroRatedSalesPage.enterInvalidEstimatedVatTurnover(amount)
    clickSaveAndContinue()
  }

  Then("""^I will see the error "Estimate the value of the company's zero-rated sales for the next 12 months" on the estimate zero-rated sales page$"""){ () =>
    EstimateZeroRatedSalesPage.checkEmptyFieldError()
  }

  Then("""^I will see the error 'Enter a positive estimate' on the estimate zero-rated sales page$"""){ () =>
    EstimateZeroRatedSalesPage.checkEnterPositiveError()
  }

  Then("""^I will see the error 'Enter an estimate which is less than 1,000,000,000,000,000' on the estimate zero-rated sales page$"""){ () =>
    EstimateZeroRatedSalesPage.checkEnterLessThanQuadrillionError()
  }

  Then("""^I attempt to type or paste an invalid character on the estimate zero-rated sales page$"""){ () =>
    EstimateZeroRatedSalesPage.enterInvalidCharacters()
  }

  Then("""^I will see that the characters do not appear in the numeric field on the estimate zero-rated sales page$"""){ () =>
    EstimateZeroRatedSalesPage.checkFieldEmpty()
  }

  Then("""^the value I entered for the zero-rated sales will be pre-populated$"""){ () =>
    EstimateZeroRatedSalesPage.checkPrepopulateZeroRatedSales()
  }

  Then("""^I provide a different estimated zero-rated sales value$"""){ () =>
    EstimateZeroRatedSalesPage.differentEstimatedZeroRatedSales()
  }
}
