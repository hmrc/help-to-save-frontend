package uk.gov.hmrc.integration.cucumber.pages.sicAndCompliance.labour

import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage


object TemporaryContractsCompliancePage extends BasePage {

  override val url: String = s"$basePageUrl/compliance/temporary-contracts"

  def temporaryContractsComplianceRadioButton: RadioButtonGroup = radioButtonGroup("temporaryContractsRadio")

  def checkBodyTitle(): Boolean = checkBodyTitle(getMessage("tccp.body.header"))

  def clickTemporaryContractsComplianceOption(option: String) = option match {
    case "Yes"  => temporaryContractsComplianceRadioButton.value = "TEMP_CONTRACTS_YES"
    case "No"  => temporaryContractsComplianceRadioButton.value = "TEMP_CONTRACTS_NO"
  }

  def checkTemporaryContractsComplianceOption(option: String) = option match {
    case "Yes"  => temporaryContractsComplianceRadioButton.value shouldBe "TEMP_CONTRACTS_YES"
    case "No"  => temporaryContractsComplianceRadioButton.value shouldBe "TEMP_CONTRACTS_NO"
  }

  def checkRadioError() = validateErrorMessages("temporaryContractsRadio", "error.temporaryContractsCompliance.noSelection")

}
