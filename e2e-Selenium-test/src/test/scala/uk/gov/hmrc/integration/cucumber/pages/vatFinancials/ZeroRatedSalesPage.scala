package uk.gov.hmrc.integration.cucumber.pages.vatFinancials

import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage

object ZeroRatedSalesPage extends BasePage {

  override val url: String = s"$basePageUrl/zero-rated-sales"

  def zeroRatedRadioButton: RadioButtonGroup = radioButtonGroup("zeroRatedSalesRadio")

  def checkBodyTitle(): Boolean = checkBodyTitle(getMessage("zsp.body.header"))

  def clickZeroRatedSalesOption(option: String) = option match {
    case "Yes" => zeroRatedRadioButton.value = "ZERO_RATED_SALES_YES"
    case "No"  => zeroRatedRadioButton.value = "ZERO_RATED_SALES_NO"
  }

  def checkZeroRatedSalesOption(option: String) = option match {
    case "Yes" => zeroRatedRadioButton.value shouldBe "ZERO_RATED_SALES_YES"
    case "No"  => zeroRatedRadioButton.value shouldBe "ZERO_RATED_SALES_NO"
  }

  // def checkRadioError() = validateErrorMessages("zeroRatedSalesRadio", "error.noSelection")

  def checkRadioError() = validateErrorMessages("zeroRatedSalesRadio", "error.zeroRatedSalesRadioError")
}
