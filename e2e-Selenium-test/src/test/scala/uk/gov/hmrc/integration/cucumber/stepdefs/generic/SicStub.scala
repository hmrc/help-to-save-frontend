package uk.gov.hmrc.integration.cucumber.stepdefs.generic

import cucumber.api.scala.{EN, ScalaDsl}
import uk.gov.hmrc.integration.cucumber.pages.generic.{BasePage, SicStubPage}


class SicStub extends BasePage {

  Then("""^I will be prompted to provide up to 4, 8 digits standard industry codes in stub page$""") { () =>
    go to SicStubPage
    SicStubPage.checkBodyTitle()
  }

  Then("""^I will be presented with the sic-stub page$""") { () =>
    SicStubPage.checkBodyTitle()
  }

  And("""^I submit after providing up to 4 cultural compliance codes$""") { () =>
    SicStubPage.enterCulturalComplianceSicCodes()
    clickOn("submit")
  }

  And("""^I submit after providing up to 4 labour compliance codes$""") { () =>
    SicStubPage.enterLabourComplianceSicCodes()
    clickOn("submit")
  }

  And("""^I submit after providing up to 4 financial compliance codes$""") { () =>
    SicStubPage.enterFinancialComplianceSicCodes()
    clickOn("submit")
  }

  And("""^I submit after providing one non-compliance sic code$""") { () =>
    SicStubPage.enterNonComplianceSicCodes()
    clickOn("submit")
  }

  And("""^I submit after providing financial-compliance sic codes$""") { () =>
    SicStubPage.enterFinancialComplianceSicCodes()
    clickOn("submit")
  }

}
