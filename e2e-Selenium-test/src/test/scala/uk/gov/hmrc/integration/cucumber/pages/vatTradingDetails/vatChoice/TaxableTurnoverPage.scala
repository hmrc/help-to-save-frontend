package uk.gov.hmrc.integration.cucumber.pages.vatTradingDetails.vatChoice

import org.scalatest.Assertion
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage

object TaxableTurnoverPage extends BasePage{

  override val url: String = s"$basePageUrl/taxable-turnover"

  def taxableTurnoverRadioButton: RadioButtonGroup = radioButtonGroup("taxableTurnoverRadio")

  def checkBodyTitle(): Boolean = checkBodyTitle(getMessage("ttp.body.header"))

  def clickTaxableOption(option: String): Unit = clickOptionYesNo(option, taxableTurnoverRadioButton, "TAXABLE_YES", "TAXABLE_NO")

  def checkTaxableOption(option: String): Unit = checkOptionYesNo(option, taxableTurnoverRadioButton, "TAXABLE_YES", "TAXABLE_NO")

  def checkRadioError(): Assertion = validateErrorMessages("taxableTurnoverRadio", "error.turnover83k.empty")


}
