package uk.gov.hmrc.integration.cucumber.stepdefs.summary

import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.summary.SummaryPage
import uk.gov.hmrc.integration.cucumber.pages.vatTradingDetails.vatChoice.StartDatePage


trait VatDetails extends BasePage {

  When("""^I attempt to edit the data displayed to me \(where it's the date of incorporation\)$"""){ () =>
    SummaryPage.verifyVatStartDate()
    SummaryPage.changeVatStartDate()
  }

  When("""^I attempt to edit the data displayed to me \(where it's a date I provided\)$"""){ () =>
    SummaryPage.changeVatStartDate()
  }

  Then("""^I will be prompted to provide a voluntary start date$"""){ () =>
    BasePage.waitForPageToBeLoaded(StartDatePage.checkBodyTitle(), "Failed to load Start Date Page")
  }

  Then("""^the selection of 'date of incorporation' I made will be pre-selected$"""){ () =>
    StartDatePage.checkUseDateWhenRegistered()
  }

  Then("""^the selection of 'business start date' I made will be pre-selected$"""){ () =>
    StartDatePage.clickUseWhenBusinessStartDate()
  }

  Then("""^the summary will be displayed with the date$"""){ () =>
    SummaryPage.verifyVatStartDate()
  }

  Then("""^the summary will be displayed with the date and the option to edit$"""){ () =>
    SummaryPage.verifyVatStartDate()
    SummaryPage.verifyChangeStartDate()
  }

  Then("""^the date I entered will be pre-populated$"""){ () =>
    StartDatePage.checkSpecificStartDate()
  }

  Then("""^when I provide a different date$"""){ () =>
    StartDatePage.provideDifferentDate()
  }

  Then("""^the summary will be displayed with the new date$"""){ () =>
    SummaryPage.verifyVatStartDate()
  }

  Then("""^when I select ' from the date of incorporation' instead of providing a date$"""){ () =>
    StartDatePage.clickUseDateWhenRegistered()
  }

  Then("""^the summary will be displayed with my selection$"""){ () =>
    SummaryPage.verifyVatStartDate()
  }

  Then("""^I select 'Change' on the trading name$"""){ () =>
    SummaryPage.changeTradingName()
  }

  Then("""^I will be taken to the summary which displays 'Other trading name' and the new trading name I provided$"""){ () =>
    SummaryPage.verifyTradingName()
  }

  Then("""^I will be taken to the summary which displays 'Other trading name' and 'No' will be displayed$"""){ () =>
    SummaryPage.verifyTradingName()
  }

  Then("""^I will be taken to the summary which displays 'Other trading name' and the trading name I provided will be displayed$"""){ () =>
    SummaryPage.verifyTradingName()
  }

  Then("""^I select 'Change' on the taxable turnover question$"""){ () =>
    SummaryPage.changeTaxableTurnover()
  }

  Then("""^the summary will display '(.*)' on the taxable turnover question$"""){ (answer: String) =>
    SummaryPage.verifyTaxableTurnover(answer)
  }

  // Voluntary Registration Reason
  Then("""^The summary will display '(.*)' on the voluntary registration reason$""") { (reason:String) =>
    SummaryPage.verifyVoluntaryRegistrationReason(reason)
  }

  When("""^I attempt to edit the Voluntary registration reason displayed to me from summary$""") { () =>
    SummaryPage.changeVoluntaryRegistrationReason()
  }

  // Register voluntarily
  Then("""^The summary will display 'Yes' on the Register voluntarily$"""){ () =>
    SummaryPage.verifyRegisterVoluntarilyYes()
  }

  And("""^I select 'Change' on the Register voluntarily$"""){ () =>
    SummaryPage.changeRegisterVoluntarily()
  }

  Then("""^the summary will display 'Date of Incorporation' on the VAT start date"""){ () =>
    SummaryPage.verifyVatStartDate()
  }

}
