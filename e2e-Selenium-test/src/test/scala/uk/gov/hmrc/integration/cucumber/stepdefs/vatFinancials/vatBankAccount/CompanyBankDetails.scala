package uk.gov.hmrc.integration.cucumber.stepdefs.vatFinancials.vatBankAccount

import cucumber.api.scala.{EN, ScalaDsl}
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.vatFinancials.vatBankAccount.CompanyBankDetailsPage


class CompanyBankDetails extends BasePage {

  When("""^I am presented with the 'Bank Details' page$"""){ () =>
    go to CompanyBankDetailsPage
    CompanyBankDetailsPage.checkBodyTitle()
  }

  Then("""^I provide the company bank account details$"""){ () =>
    CompanyBankDetailsPage.provideCompanyBankAccountDetails()
  }

  Then("""^I attempt to type or paste an invalid character on the 'Bank Details' page which will not display$"""){ () =>
    CompanyBankDetailsPage.enterInvalidCharacters()
  }

  Then("""^I will be presented with the company bank details page$""") { () =>
    CompanyBankDetailsPage.checkBodyTitle()
  }

  And("""^I will see the error 'Enter a business account name' on the 'Bank Details' page$""") { () =>
    CompanyBankDetailsPage.checkCompanyBankAccountNameError()
  }

  And("""^I will see the error 'Enter a business account number' on the 'Bank Details' page$""") { () =>
    CompanyBankDetailsPage.checkCompanyBankAccountNumberError()
  }

  And("""^I will see the error 'Enter a sort code' on the 'Bank Details' page$""") { () =>
    CompanyBankDetailsPage.checkSortCodeError()
  }

  And("""^only the bank account number will not be pre-populated$""") { () =>
    CompanyBankDetailsPage.checkCompanyBankAccountDetails()
  }

  Then("""^I provide a different company bank account details$""") { () =>
    CompanyBankDetailsPage.provideDifferentCompanyBankAccountDetails()
  }

}
