package uk.gov.hmrc.integration.cucumber.pages.vatFinancials

import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage


object EstimateZeroRatedSalesPage extends BasePage {

  override val url: String = s"$basePageUrl/estimate-zero-rated-sales"

  def checkBodyTitle(): Boolean = checkBodyTitle(getMessage("ezsp.body.header"))

  var estimatedZeroRatedSalesValue: String = "£1234567"

  def initialise() = {
    estimatedZeroRatedSalesValue = "£1234567"
  }

  def estimatedZeroRatedSalesField: NumberField = numberField("zeroRatedTurnoverEstimate")

  def estimatedZeroRatedSales() = {
    estimatedZeroRatedSalesValue = "£70000"
    estimatedZeroRatedSalesField.value = estimatedZeroRatedSalesValue
  }
  def differentEstimatedZeroRatedSales() = {
    estimatedZeroRatedSalesValue = "£19120000"
    estimatedZeroRatedSalesField.value = estimatedZeroRatedSalesValue
  }

  def enterInvalidEstimatedVatTurnover(amount: String) = amount match {
    case "too low"  => estimatedZeroRatedSalesField.value = "-100"
    case "too high" => estimatedZeroRatedSalesField.value = "1,000,000,000,000,000,00"
  }

  def enterInvalidCharacters() = estimatedZeroRatedSalesField.value = "invalid;|[]@!@£$%^&*()"

  def checkFieldEmpty() = estimatedZeroRatedSalesField.value shouldBe 'empty

  def checkEmptyFieldError() = validateErrorMessages("zeroRatedTurnoverEstimate", "error.estimatezero.empty")
  def checkEnterPositiveError() = validateErrorMessages("zeroRatedTurnoverEstimate", "error.positive")
  def checkEnterLessThanQuadrillionError() = validateErrorMessages("zeroRatedTurnoverEstimate", "error.lessThanQuadrillion")

  def checkPrepopulateZeroRatedSales(): Unit = estimatedZeroRatedSalesField.value shouldBe estimatedZeroRatedSalesValue.replaceFirst("^£", "")
}
