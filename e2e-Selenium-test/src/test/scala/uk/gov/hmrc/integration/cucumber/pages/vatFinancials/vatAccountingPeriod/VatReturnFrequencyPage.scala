package uk.gov.hmrc.integration.cucumber.pages.vatFinancials.vatAccountingPeriod

import org.scalatest.Assertion
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage


object VatReturnFrequencyPage extends BasePage {

  override val url: String = s"$basePageUrl/vat-return-frequency"

  def vatReturnFrequencyRadioButton: RadioButtonGroup = radioButtonGroup("vatReturnFrequencyRadio")

  def checkBodyTitle(): Boolean = checkBodyTitle(getMessage("vrf.body.header"))

  def clickVatReturnFrequency(option: String): Unit = option match {
    case "monthly"    => vatReturnFrequencyRadioButton.value = "monthly"
    case "quarterly"  => vatReturnFrequencyRadioButton.value = "quarterly"
  }

  def checkVatReturnFrequency(option: String): Assertion = option match {
    case "monthly"    => vatReturnFrequencyRadioButton.value shouldBe "monthly"
    case "quarterly"  => vatReturnFrequencyRadioButton.value shouldBe "quarterly"
  }

  def radioErrorMessage: String = findById("vatReturnFrequencyRadio-error-summary").getText
  def errorMessage(): Assertion = radioErrorMessage shouldBe getMessage("error.howoftenvat.empty")

}
