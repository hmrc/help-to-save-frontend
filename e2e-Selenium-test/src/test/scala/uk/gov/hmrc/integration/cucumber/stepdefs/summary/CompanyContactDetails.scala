package uk.gov.hmrc.integration.cucumber.stepdefs.summary

import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.summary.SummaryPage

trait CompanyContactDetails extends BasePage {

  Then("""^the summary will display the email address and daytime phone number on respective question$"""){ () =>
    SummaryPage.checkBusinessEmailAddress()
    SummaryPage.checkBusinessDayPhoneNo()
  }

  Then("""^the summary will display the email address and mobile number on respective question$"""){ () =>
    SummaryPage.checkBusinessEmailAddress()
    SummaryPage.checkBusinessMobileNo()
  }

  Then("""^the summary will display the email address, mobile number and email address on respective question$"""){ () =>
    SummaryPage.checkBusinessEmailAddress()
    SummaryPage.checkBusinessDayPhoneNo()
    SummaryPage.checkBusinessMobileNo()
  }
}
