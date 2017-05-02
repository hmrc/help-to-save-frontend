package uk.gov.hmrc.integration.cucumber.pages.vatFinancials

import org.scalatest.Assertion
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage


object VatChargeExpectancyPage extends BasePage {

  override val url:String = s"$basePageUrl/vat-charge-expectancy"

  def vatChargeRadioButton: RadioButtonGroup = radioButtonGroup("vatChargeRadio")

  def checkBodyTitle(): Boolean = checkBodyTitle(getMessage("ervrp.body.header"))

  def clickVatChargeOption(option: String): Unit = clickOptionYesNo(option, vatChargeRadioButton ,"VAT_CHARGE_YES", "VAT_CHARGE_NO")

  def checkVatChargeOption(option: String): Assertion = checkOptionYesNo(option, vatChargeRadioButton ,"VAT_CHARGE_YES", "VAT_CHARGE_NO")

  def checkRadioError(): Assertion = validateErrorMessages("vatChargeRadio", "error.expectreclaimVat.empty")
}
