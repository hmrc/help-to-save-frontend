package uk.gov.hmrc.integration.cucumber.stepdefs.summary

import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.summary.SummaryPage

trait ProvidingFinancialServices extends BasePage {

  Then("""^the summary will display '(.*)' on the advice or consultancy question$"""){ (answer: String) =>
    SummaryPage.verifyAdviceOrConsultancyService(answer)
  }

  And("""^I select 'Change' on the advice or consultancy question$"""){ () =>
    SummaryPage.changeAdviceOrConsultancyService()
  }

  Then("""^the summary will display '(.*)' on the intermediary question$"""){ (answer: String) =>
    SummaryPage.verifyIntermediary(answer)
    SummaryPage.checkRowsHidden(answer)
  }

  And("""^I select 'Change' on the intermediary question$"""){ () =>
    SummaryPage.changeIntermediary()
  }

  Then("""^the summary will display '(.*)' on the charge fees question$"""){ (answer: String) =>
    SummaryPage.verifyChargeFees(answer)
  }

  And("""^I select 'Change' on the charge fees question$"""){ () =>
    SummaryPage.changeChargeFees()
  }

  Then("""^the summary will display '(.*)' on the additional non securities work question$"""){ (answer: String) =>
    SummaryPage.verifyAdditionalNonSecuritiesWork(answer)
  }

  And("""^I select 'Change' on the additional non securities work question$"""){ () =>
    SummaryPage.changeAdditionalNonSecuritiesWork()
  }

  Then("""^the summary will display '(.*)' on the discretionary investment management service question$"""){ (answer: String) =>
    SummaryPage.verifyDiscretionaryInvestmentManagementServices(answer)
  }

  And("""^I select 'Change' on the discretionary investment management service question$"""){ () =>
    SummaryPage.changeDiscretionaryInvestmentManagementServices()
  }

  Then("""^the summary will display '(.*)' on the leasing vehicles or equipments question$"""){ (answer: String) =>
    SummaryPage.verifyLeasingVehiclesOrEquipments(answer)
  }

  And("""^I select 'Change' on the leasing vehicles or equipments question$"""){ () =>
    SummaryPage.changeLeasingVehiclesOrEquipments()
  }

  Then("""^the summary will display '(.*)' on the investment fund management service question$"""){ (answer: String) =>
    SummaryPage.verifyInvestmentFundManagementService(answer)
  }

  And("""^I select 'Change' on the investment fund management service question$"""){ () =>
    SummaryPage.changeInvestmentFundManagementService()
  }

  Then("""^the summary will display '(.*)' on the manage additional funds question$"""){ (answer: String) =>
    SummaryPage.verifyManageAdditionalFunds(answer)
  }

  And("""^I select 'Change' on the manage additional funds question$"""){ () =>
    SummaryPage.changeManageAdditionalFunds()
  }


}
