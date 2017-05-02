package uk.gov.hmrc.integration.cucumber.pages.vatTradingDetails.vatChoice

import org.scalatest.Assertion
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage


object VoluntaryRegistrationReasonPage extends BasePage {

  override val url: String = s"$basePageUrl/voluntary-registration-reason"

  def checkBodyTitle(): Boolean = checkBodyTitle(getMessage("vrrp.body.header"))

  def voluntaryRegistrationReasonRadio: RadioButtonGroup = radioButtonGroup("voluntaryRegistrationReasonRadio")

  def clickVoluntaryRegistrationReasonOption(reason: String): Unit =  reason match {
    case "the company already sells..." => voluntaryRegistrationReasonRadio.value = "COMPANY_ALREADY_SELLS_TAXABLE_GOODS_OR_SERVICES"
    case "the company intends to sell..."  => voluntaryRegistrationReasonRadio.value = "COMPANY_INTENDS_TO_SELLS_TAXABLE_GOODS_OR_SERVICES_IN_THE_FUTURE"
    case "None of these apply"  => voluntaryRegistrationReasonRadio.value = "NEITHER"
  }

  def checkVoluntaryRegistrationReasonOption(reason: String): Assertion =  reason match {
    case "the company already sells..." => voluntaryRegistrationReasonRadio.value shouldBe "COMPANY_ALREADY_SELLS_TAXABLE_GOODS_OR_SERVICES"
    case "the company intends to sell..."  => voluntaryRegistrationReasonRadio.value shouldBe "COMPANY_INTENDS_TO_SELLS_TAXABLE_GOODS_OR_SERVICES_IN_THE_FUTURE"
    case "None of these apply"  => voluntaryRegistrationReasonRadio.value shouldBe "NEITHER"
  }

  def checkRadioError(): Assertion = validateErrorMessages("voluntaryRegistrationReasonRadio", "error.voluntaryRegistrationReason.noSelection")


}
