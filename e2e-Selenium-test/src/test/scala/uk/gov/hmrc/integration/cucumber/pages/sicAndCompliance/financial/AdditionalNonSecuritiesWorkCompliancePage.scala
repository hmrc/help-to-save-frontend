package uk.gov.hmrc.integration.cucumber.pages.sicAndCompliance.financial

import org.scalatest.Assertion
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage


object AdditionalNonSecuritiesWorkCompliancePage extends BasePage{

  override val url:String = s"$basePageUrl/does-additional-work-when-introducing-client-to-financial-service-provider"

  def checkBodyTitle: Boolean = checkBodyTitle(getMessage("answcp.body.header"))

  def additionalNonSecuritiesWorkComplianceRadioButton: RadioButtonGroup = radioButtonGroup("additionalNonSecuritiesWorkRadio")

  def clickAdditionalNonSecuritiesWorkOption(option: String): Unit = clickOptionYesNo(option, additionalNonSecuritiesWorkComplianceRadioButton, "true", "false")

  def checkAdditionalNonSecuritiesWorkOption(option: String): Assertion = checkOptionYesNo(option, additionalNonSecuritiesWorkComplianceRadioButton, "true", "false")

  def checkRadioError(): Assertion = validateErrorMessages("additionalNonSecuritiesWorkRadio", "error.additionalNonSecuritiesWorkCompliance.noSelection")
}