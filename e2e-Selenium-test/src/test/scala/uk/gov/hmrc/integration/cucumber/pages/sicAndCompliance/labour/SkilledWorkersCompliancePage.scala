package uk.gov.hmrc.integration.cucumber.pages.sicAndCompliance.labour

import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage


object SkilledWorkersCompliancePage extends BasePage {

  override val url: String = s"$basePageUrl/compliance/skilled-workers"

  def checkBodyTitle(): Unit = checkBodyTitle(getMessage("swcp.body.header"))

  def skilledLaboursRadio: RadioButtonGroup = radioButtonGroup("skilledWorkersRadio")

  def clickSkilledWorkersOption(skilledWorkersOption: String) =  skilledWorkersOption match {
    case "Yes" => skilledLaboursRadio.value = "SKILLED_WORKERS_YES"
    case "No"  => skilledLaboursRadio.value = "SKILLED_WORKERS_NO"
  }

  def skilledWorkersRadioError(): Unit = validateErrorMessages("skilledWorkersRadio", "error.skilledWorkers.noSelection")

}
