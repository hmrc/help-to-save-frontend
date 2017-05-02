package uk.gov.hmrc.integration.cucumber.stepdefs

import uk.gov.hmrc.integration.cucumber.pages.ApplicationSubmittedPage
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage


class ApplicationSubmitted extends BasePage{

  Then("""^I will be prompted to application submitted page which displays application reference number$"""){ () =>
    go to ApplicationSubmittedPage
  }

}
