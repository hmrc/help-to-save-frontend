package uk.gov.hmrc.integration.cucumber.pages.vatFinancials

import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage


object EstimateVatTurnoverPage extends BasePage {

  override val url: String = s"$basePageUrl/estimate-vat-turnover"

  def checkBodyTitle(): Boolean = checkBodyTitle(getMessage("evtp.body.header"))

  var estimatedVatTurnoverValue: String = "£45678"

  def initialise() = {
    estimatedVatTurnoverValue = "£45678"
  }
  def estimatedVatTurnoverField: NumberField = numberField("turnoverEstimate")

  def estimatedVatTurnover() = {
    estimatedVatTurnoverValue = "£40000"
    estimatedVatTurnoverField.value = estimatedVatTurnoverValue
  }

  def differentEstimatedVatTurnover() = {
    estimatedVatTurnoverValue = "£19921219"
    estimatedVatTurnoverField.value = estimatedVatTurnoverValue
  }

  def enterInvalidEstimatedVatTurnover(amount: String) = amount match {
    case "too low"  => estimatedVatTurnoverField.value = "-100"
    case "too high" => estimatedVatTurnoverField.value = "1,000,000,000,000,000,00"
  }

  def enterVatInvalidCharacters() = estimatedVatTurnoverField.value = "invalid;|[]@!@£$%^&*()"
  def checkVatFieldEmpty() = estimatedVatTurnoverField.value shouldBe 'empty

  def checkEmptyFieldError() = validateErrorMessages("turnoverEstimate", "error.estimate.empty")
  def checkEnterPositiveError() = validateErrorMessages("turnoverEstimate", "error.positive")
  def checkEnterLessThanQuadrillionError() = validateErrorMessages("turnoverEstimate", "error.lessThanQuadrillion")

  def prePopulatedVatTurnover(): Unit = estimatedVatTurnoverField.value shouldBe estimatedVatTurnoverValue.replaceFirst("^£", "")

}
