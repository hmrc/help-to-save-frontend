package uk.gov.hmrc.integration.cucumber.pages.sicAndCompliance.financial

import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage

object IntermediaryPage extends BasePage{

  override val url: String = s"$basePageUrl/compliance/act-as-intermediary"

  def checkBodyTitle(): Boolean = checkBodyTitle(getMessage("fci.body.header"))

  def intermediaryRadios: RadioButtonGroup = radioButtonGroup("actAsIntermediaryRadio")

  def clickIntermediaryRadio(option: String): Unit = clickOptionYesNo(option, intermediaryRadios, "true", "false")

  def checkIntermediaryRadioOption(option: String): Unit = checkOptionYesNo(option, intermediaryRadios, "true", "false")

  def checkIntermediaryRadioError(): Unit = validateErrorMessages("actAsIntermediaryRadio", "error.intermediaryRadio.noSelection")
}
