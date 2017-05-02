package uk.gov.hmrc.integration.cucumber.pages.vatFinancials.vatAccountingPeriod

import org.scalatest.Assertion
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage

object AccountingPeriodPage extends BasePage {

  override val url: String = s"$basePageUrl/accounting-period"

  def accountingPeriodRadioButton: RadioButtonGroup = radioButtonGroup("accountingPeriodRadio")

  def checkBodyTitle(): Boolean = checkBodyTitle(getMessage("ap.body.header"))

  def clickAccountingPeriodOption(option: String): Unit = option match {
    case "January" => accountingPeriodRadioButton.value = "JAN_APR_JUL_OCT"
    case "February"  => accountingPeriodRadioButton.value = "FEB_MAY_AUG_NOV"
    case "March"  => accountingPeriodRadioButton.value = "MAR_JUN_SEP_DEC"
  }

  def checkAccountingPeriodOption(option: String): Assertion = option match {
    case "January" => accountingPeriodRadioButton.value shouldBe "JAN_APR_JUL_OCT"
    case "February"  => accountingPeriodRadioButton.value shouldBe "FEB_MAY_AUG_NOV"
    case "March"  => accountingPeriodRadioButton.value shouldBe "MAR_JUN_SEP_DEC"
  }

  def radioErrorMessage: String = findById("accountingPeriodRadio-error-summary").getText
  def errorMessage(): Assertion = radioErrorMessage shouldBe getMessage("error.whenWantYourVatReturn")
}
