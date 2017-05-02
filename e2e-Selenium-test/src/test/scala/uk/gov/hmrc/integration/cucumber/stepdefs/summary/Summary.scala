package uk.gov.hmrc.integration.cucumber.stepdefs.summary

import uk.gov.hmrc.integration.cucumber.pages.summary.SummaryPage


class Summary extends CompanyContactDetails
  with CompanyDetails
  with VatDetails
  with ProvidingFinancialServices {

  Given("""^I am on the summary$"""){ () =>
    go to SummaryPage
    SummaryPage.checkBodyTitle()
  }

  And("""^I navigate to the summary page$"""){ () =>
    go to SummaryPage
  }

  Then("""^I will be presented with the summary$"""){ () =>
    SummaryPage.checkBodyTitle()
  }
}
