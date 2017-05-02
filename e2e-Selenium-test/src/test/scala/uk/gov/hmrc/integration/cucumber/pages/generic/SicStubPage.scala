package uk.gov.hmrc.integration.cucumber.pages.generic

object SicStubPage extends BasePage {

  override val url: String = s"$basePageUrl/test-only/sic-stub"

  def enterSicCode1: TextField = textField("sicCode1")
  def enterSicCode2: TextField = textField("sicCode2")
  def enterSicCode3: TextField = textField("sicCode3")
  def enterSicCode4: TextField = textField("sicCode4")

  def enterCulturalComplianceSicCodes():Unit = {
    enterSicCode1.value = "90010123"
    enterSicCode2.value = "90020123"
  }

  def enterNonComplianceSicCodes():Unit = {
    enterSicCode1.value = "12345678"
  }

  def enterLabourComplianceSicCodes():Unit ={
    enterSicCode1.value = "81221321"
    enterSicCode2.value = "81221321"
  }

  def enterFinancialComplianceSicCodes():Unit ={
    enterSicCode1.value = "70221123"
    enterSicCode2.value = "66110123"
    enterSicCode3.value = "66300123"
  }

  def checkBodyTitle(): Boolean = checkBodyTitle("Enter 1-4, 8 digit SIC codes:")
}
