package uk.gov.hmrc.integration.cucumber.pages.sicAndCompliance.financial

import org.scalatest.Assertion
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage

object ChargeFeesCompliancePage extends BasePage {

  override val url:String = s"$basePageUrl/charges-fees-for-introducing-clients-to-financial-service-providers"

  def checkBodyTitle(): Boolean = checkBodyTitle(getMessage("cfcp.body.header"))

  def chargeFeesRadioButton: RadioButtonGroup = radioButtonGroup("chargeFeesRadio")

  def clickChargeFeesOption(option: String): Unit = clickOptionYesNo(option, chargeFeesRadioButton, "true", "false")

  def checkChargeFeesOption(option: String): Assertion = checkOptionYesNo(option, chargeFeesRadioButton, "true", "false")

  def checkRadioError(): Assertion = validateErrorMessages("chargeFeesRadio", "error.chargeFeesCompliance.noSelection")
}