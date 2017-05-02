package uk.gov.hmrc.integration.cucumber.stepdefs.vatTradingDetails.vatChoice

import cucumber.api.scala.{EN, ScalaDsl}
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.vatTradingDetails.vatChoice.StartDatePage

class StartDate extends BasePage {

  Given("""^I am prompted to provide a voluntary start date$"""){ () =>
    go to StartDatePage
    StartDatePage.checkBodyTitle()
  }

  Given("""^I am on the voluntary start date page$"""){ () =>
    go to StartDatePage
    StartDatePage.checkBodyTitle()
  }

  When("""^I request to be registered for VAT from the date of incorporation$"""){ () =>
    StartDatePage.clickUseDateWhenRegistered()
  }

  When("""^I provide a date which is (\d+) days in the future$"""){ (daysInFuture:Int) =>
    StartDatePage.clickProvideFutureDate()
    StartDatePage.provideFutureStartDate(daysInFuture)
  }

  When("^I provide a past date$"){ () =>
    StartDatePage.providePastStartDate()
  }

  And("""^I request to provide a specific start date$"""){() =>
    StartDatePage.clickProvideFutureDate()
  }
  And("""^I attempt to continue without selecting a start date$"""){ () =>
    clickSaveAndContinue()
  }

  And("""^I attempt to continue without providing a date$"""){ () =>
    clickSaveAndContinue()
  }

  Then("""^I will be presented with the Start Date page$""") { () =>
    StartDatePage.checkBodyTitle()
  }

  Then("""^I will see an error 'Please enter a start date'$"""){ () =>
    StartDatePage.checkStartDateEmptyError()
  }

  Then("""^I will see an error 'Tell us when you want your VAT start date to be' on the start date page$"""){ () =>
    StartDatePage.checkRadioError()
  }

  Then("""^I will see an error 'Date must be more than 2 working days in the future'$"""){ () =>
    StartDatePage.checkDateLessThan2WorkingDaysError()
  }

  Then("""^I will see an error 'Date must be within the next 3 months'$"""){ () =>
    StartDatePage.checkDateAfter3MonthsError()
  }
}
