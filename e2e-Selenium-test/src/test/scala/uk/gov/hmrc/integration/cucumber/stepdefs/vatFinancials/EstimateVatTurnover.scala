package uk.gov.hmrc.integration.cucumber.stepdefs.vatFinancials

import cucumber.api.scala.{EN, ScalaDsl}
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.vatFinancials.EstimateVatTurnoverPage


class EstimateVatTurnover extends BasePage {


  Given("""^I am presented with the 'Estimated VAT taxable turnover' page$"""){ () =>
    go to EstimateVatTurnoverPage
    EstimateVatTurnoverPage.checkBodyTitle()
  }

  When("""^I provide an estimated vat turnover value$"""){ () =>
    EstimateVatTurnoverPage.estimatedVatTurnover()
  }

  When("""^I provide a different estimated vat turnover value$"""){ () =>
    EstimateVatTurnoverPage.differentEstimatedVatTurnover()
  }

  When("""^I attempt to continue without entering a value on the taxable turnover estimate page$"""){ () =>
    clickSaveAndContinue()
  }

  Then("""^I will be presented with estimated VAT Taxable Turnover page$"""){() =>
    EstimateVatTurnoverPage.checkBodyTitle()
  }

  Then("""^when I enter a value which is (.*) on the taxable turnover estimate page$"""){ (amount: String) =>
    EstimateVatTurnoverPage.enterInvalidEstimatedVatTurnover(amount)
    clickSaveAndContinue()
  }

  Then("""^I will see the error "Estimate the company's VAT taxable turnover for the next 12 months" on the taxable turnover estimate page$"""){ () =>
    EstimateVatTurnoverPage.checkEmptyFieldError()
  }

  Then("""^I will see the error 'Enter a positive estimate' on the taxable turnover estimate page$"""){ () =>
    EstimateVatTurnoverPage.checkEnterPositiveError()
  }

  Then("""^I will see the error 'Enter an estimate which is less than 1,000,000,000,000,000' on the taxable turnover estimate page$"""){ () =>
    EstimateVatTurnoverPage.checkEnterLessThanQuadrillionError()
  }

  And("""^the value I entered will be pre-populated$"""){ () =>
    EstimateVatTurnoverPage.prePopulatedVatTurnover()
  }

  When("""^I attempt to type or paste an invalid character on the taxable turnover estimate page$"""){ () =>
    EstimateVatTurnoverPage.enterVatInvalidCharacters()
  }

  Then("""^I will see that the characters do not appear in the numeric field on the taxable turnover estimate page$"""){ () =>
    EstimateVatTurnoverPage.checkVatFieldEmpty()
  }


}
