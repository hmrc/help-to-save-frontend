package uk.gov.hmrc.integration.cucumber.stepdefs.sicAndCompliance.cultural

import cucumber.api.scala.{EN, ScalaDsl}
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.sicAndCompliance.cultural.NotForProfitCompliancePage

class NotForProfitCompliance extends BasePage {

  And("""^I will be prompted to not-for-profit compliance question page$""") { () =>
    go to NotForProfitCompliancePage
    NotForProfitCompliancePage.checkBodyTitle()
  }

  When("""^I answered '(.*)' for Is your company a not-for-profit organisation or public body? in compliance question page$""") { (complianceAnswer: String) =>
    NotForProfitCompliancePage.clickNotForProfitOption(complianceAnswer)
  }

  Then("""^I will be presented with the Not-For-Profit compliance question page$""") { () =>
    NotForProfitCompliancePage.checkBodyTitle()
  }

  Then("""^I will see an error 'Tell us if your company is a not-for-profit organisation or public body' on the cultural compliance question page$""") { () =>
    NotForProfitCompliancePage.checkComplianceRadioError()
  }
}
