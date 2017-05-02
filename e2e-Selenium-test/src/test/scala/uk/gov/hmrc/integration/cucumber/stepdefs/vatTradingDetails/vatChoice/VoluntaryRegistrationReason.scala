package uk.gov.hmrc.integration.cucumber.stepdefs.vatTradingDetails.vatChoice

import cucumber.api.scala.{EN, ScalaDsl}
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.vatTradingDetails.vatChoice.VoluntaryRegistrationReasonPage


class VoluntaryRegistrationReason extends BasePage {

  When("""^I am presented with the voluntary registration reason page$"""){ () =>
    go to VoluntaryRegistrationReasonPage
    VoluntaryRegistrationReasonPage.checkBodyTitle()
  }

  And("""^I indicate that '(.*)'$"""){ (reason: String) =>
    VoluntaryRegistrationReasonPage.clickVoluntaryRegistrationReasonOption(reason)
  }

  Then("""^I will see an error 'Select an option' on the voluntary registration reason page$"""){ () =>
    VoluntaryRegistrationReasonPage.checkRadioError()
  }

  Then("""^I will be presented with the voluntary registration reason page$"""){ () =>
    VoluntaryRegistrationReasonPage.checkBodyTitle()
  }

  And("""^the selection of '(.*)' I made will be pre-selected in voluntary registration reason page$"""){ (reason:String) =>
    VoluntaryRegistrationReasonPage.checkVoluntaryRegistrationReasonOption(reason)
  }

}
