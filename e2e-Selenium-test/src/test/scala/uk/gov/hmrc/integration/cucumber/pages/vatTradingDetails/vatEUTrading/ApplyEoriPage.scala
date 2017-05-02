package uk.gov.hmrc.integration.cucumber.pages.vatTradingDetails.vatEUTrading

import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage


object ApplyEoriPage extends BasePage {

  override val url: String = s"$basePageUrl/apply-eori"

  def checkBodyTitle(): Boolean = checkBodyTitle(getMessage("aep.body.header"))

  def economicOperatorRadio: RadioButtonGroup = radioButtonGroup("applyEoriRadio")

  def clickEconomicOperatorOption(option: String) =  option match {
    case "Yes" => economicOperatorRadio.value = "true"
    case "No"  => economicOperatorRadio.value = "false"
  }

  def checkEconomicOperatorOption(option: String) =  option match {
    case "Yes" => economicOperatorRadio.value shouldBe "true"
    case "No"  => economicOperatorRadio.value shouldBe "false"
  }

  def verifyEconomicOperatorOption(option: String) =  option match {
    case "Yes, I want to apply for an EORI number now" => economicOperatorRadio.value shouldBe "true"
    case "No, I'll apply for an EORI number later"  => economicOperatorRadio.value shouldBe "false"
  }

  def changeEconomicOperatorOption(option: String) =  option match {
    case "Yes, I want to apply for an EORI number now" => economicOperatorRadio.value = "true"
    case "No, I'll apply for an EORI number later"  => economicOperatorRadio.value = "false"
  }

  def checkRadioError() = validateErrorMessages("applyEoriRadio", "error.applyEori.noSelection")

}
