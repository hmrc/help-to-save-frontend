package uk.gov.hmrc.integration.cucumber.pages.sicAndCompliance.labour

import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage


object WorkersCompliancePage extends BasePage {

  override val url: String = s"$basePageUrl/compliance/workers"

  def checkBodyTitle(): Unit = checkBodyTitle(getMessage("wcp.body.header"))

  def numberOfWorkers: TextField = textField("numberOfWorkers")

  def enterNumberOfWorkers(countOfWorkers: String): Unit = numberOfWorkers.value = countOfWorkers

  def numberOfWorkersEmptyError() = validateErrorMessages("numberOfWorkers", "error.workersCompliance.empty")

  def numberOfWorkersZeroError() = validateErrorMessages("numberOfWorkers", "error.workersCompliance.zero")

}
