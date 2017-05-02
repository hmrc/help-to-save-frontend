package uk.gov.hmrc.integration.cucumber.stepdefs.vatFinancials.vatBankAccount

import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.vatFinancials.vatBankAccount.CompanyBankAccountPage

class CompanyBankAccount extends BasePage{


  When("""^I am presented with the company bank account page$"""){ () =>
    go to CompanyBankAccountPage
    CompanyBankAccountPage.checkBodyTitle()
  }

  Then("""^I will see an error 'Tell us if you have a bank account set up in the name of the company' on the company bank account page$""") { () =>
    CompanyBankAccountPage.checkRadioError()
  }

  Then("""^I will be presented with the company bank account page$""") { () =>
    CompanyBankAccountPage.checkBodyTitle()
  }

  And("""^I select the option '(.*)' for the company bank account$""") { (bankAccount: String) =>
    CompanyBankAccountPage.clickBankAccountOption(bankAccount)
  }

  Then("""^the option '(.*)' will be pre-selected on the company bank account page$""") { (bankAccount: String) =>
    CompanyBankAccountPage.checkBankAccountOption(bankAccount)
  }

}
