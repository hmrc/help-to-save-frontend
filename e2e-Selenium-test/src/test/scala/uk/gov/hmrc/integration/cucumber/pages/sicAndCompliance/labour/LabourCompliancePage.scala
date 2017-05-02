package uk.gov.hmrc.integration.cucumber.pages.sicAndCompliance.labour

import org.scalatest.Assertion
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage


object LabourCompliancePage extends BasePage {

  override val url: String = s"$basePageUrl/compliance/provide-workers"

  def labourComplianceRadioButton: RadioButtonGroup = radioButtonGroup("companyProvideWorkersRadio")

  def checkBodyTitle(): Boolean = checkBodyTitle(getMessage("lcp.body.header"))

  def clickLabourComplianceOption(option: String): Unit = option match {
    case "Yes"  => labourComplianceRadioButton.value = "PROVIDE_WORKERS_YES"
    case "No"  => labourComplianceRadioButton.value = "PROVIDE_WORKERS_NO"
  }
  def checkLabourComplianceOption(option: String): Assertion = option match {
    case "Yes"  => labourComplianceRadioButton.value shouldBe "PROVIDE_WORKERS_YES"
    case "No"  => labourComplianceRadioButton.value shouldBe "PROVIDE_WORKERS_NO"
  }

  def checkRadioError(): Assertion = validateErrorMessages("companyProvideWorkersRadio", "error.labourCompliance.noSelection")

}
