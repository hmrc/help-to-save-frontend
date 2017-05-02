package uk.gov.hmrc.integration.cucumber.stepdefs.sicAndCompliance.labour

import cucumber.api.scala.{EN, ScalaDsl}
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.sicAndCompliance.labour.WorkersCompliancePage

class WorkersCompliance extends BasePage {


  Then("""^I am presented with the workers compliance page$""") { () =>
    go to WorkersCompliancePage
    WorkersCompliancePage.checkBodyTitle()
  }

  When("""^I enter the amount of workers provided '(.*)'$""") { (numberOfWorkers: String) =>
    WorkersCompliancePage.enterNumberOfWorkers(numberOfWorkers)
  }

  Then("""^I will see and error 'Tell us how many workers the company provides at any one time' on the labour compliance question 2 page$""") { () =>
    WorkersCompliancePage.numberOfWorkersEmptyError()
  }

  Then("""^I will see and error 'Number of workers must be at least 1' on the labour compliance question 2 page$""") { () =>
    WorkersCompliancePage.numberOfWorkersZeroError()
  }

  Then("""^I will be presented with the workers compliance page$""") { () =>
    WorkersCompliancePage.checkBodyTitle()
  }


}
