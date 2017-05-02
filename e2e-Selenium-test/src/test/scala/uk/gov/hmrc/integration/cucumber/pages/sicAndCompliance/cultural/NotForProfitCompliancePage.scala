package uk.gov.hmrc.integration.cucumber.pages.sicAndCompliance.cultural

import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage


object NotForProfitCompliancePage extends BasePage {

  override val url : String = s"$basePageUrl/compliance/not-for-profit"

  def checkBodyTitle(): Boolean = checkBodyTitle(getMessage("nfpp.body.header"))

  def notForProfitRadio: RadioButtonGroup = radioButtonGroup("notForProfitRadio")

  def clickNotForProfitOption(complianceOption: String) = complianceOption match {
    case "Yes" => notForProfitRadio.value = "NOT_PROFIT_YES"
    case "No"  => notForProfitRadio.value = "NOT_PROFIT_NO"
  }

  def checkNotForProfitOption(complianceOption: String) = complianceOption match {
    case "Yes" => notForProfitRadio.value shouldBe "NOT_PROFIT_YES"
    case "No"  => notForProfitRadio.value shouldBe "NOT_PROFIT_NO"
  }

  def checkComplianceRadioError(): Unit = validateErrorMessages("notForProfitRadio", "error.notForProfitCompliance.empty")
}
