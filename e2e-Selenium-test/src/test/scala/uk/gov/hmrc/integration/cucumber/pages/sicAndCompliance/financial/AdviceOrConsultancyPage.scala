package uk.gov.hmrc.integration.cucumber.pages.sicAndCompliance.financial

import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage

object AdviceOrConsultancyPage extends BasePage {

  override val url: String = s"$basePageUrl/compliance/advice-or-consultancy"

  def adviceOrConsultancyRadioButton: RadioButtonGroup = radioButtonGroup("adviceOrConsultancyRadio")

  def checkBodyTitle(): Boolean = checkBodyTitle(getMessage("fcaocp.body.header"))

  def clickAdviceOrConsultancyOption(option: String): Unit = clickOptionYesNo(option, adviceOrConsultancyRadioButton, "true", "false")

  def checkAdviceOrConsultancyOption(option: String): Unit = checkOptionYesNo(option, adviceOrConsultancyRadioButton, "true", "false")

  def checkAdviceOrConsultancyRadioError() = validateErrorMessages("adviceOrConsultancyRadio", "error.adviceOrConsultancyRadio.noSelection")

}
