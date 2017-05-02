package uk.gov.hmrc.integration.cucumber.stepdefs.vatFinancials.vatAccountingPeriod

import cucumber.api.scala.{EN, ScalaDsl}
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.vatFinancials.vatAccountingPeriod.AccountingPeriodPage


class AccountingPeriod extends BasePage {

  When("""^I am presented with the accounting period page$"""){ () =>
    go to AccountingPeriodPage
    AccountingPeriodPage.checkBodyTitle()
  }

  And("""^I will be presented with the accounting period page$"""){ () =>
    AccountingPeriodPage.checkBodyTitle()
  }

  And("""^I indicate I would want vat return period starting (.*)$"""){ (option: String) =>
    AccountingPeriodPage.clickAccountingPeriodOption(option)
  }

  Then("""^I will see an error 'Tell us when you want your VAT Return periods to end' on the accounting period page$"""){ () =>
    AccountingPeriodPage.errorMessage()
  }
}
