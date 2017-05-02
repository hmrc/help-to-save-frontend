package uk.gov.hmrc.integration.cucumber.pages.vatTradingDetails

import org.scalatest.Assertion
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage


object TradingNamePage extends BasePage{

  override val url: String = s"$basePageUrl/trading-name"

  def tradingNameTextField: TextField = textField("tradingName")

  def tradingNameRadioButton: RadioButtonGroup = radioButtonGroup("tradingNameRadio")

  def checkBodyTitle(): Boolean = checkBodyTitle(getMessage("tnp.body.header"))

  var tradingName = ""

  def clickProvideTradingName(): Unit = tradingNameRadioButton.value = "TRADING_NAME_YES"
  def clickNoTradingName(): Unit = {
    tradingNameRadioButton.value = "TRADING_NAME_NO"
    tradingName = "No"
  }

  def checkProvideTradingName(): Assertion = tradingNameRadioButton.value shouldBe "TRADING_NAME_YES"
  def checkNoTradingName(): Assertion = tradingNameRadioButton.value shouldBe "TRADING_NAME_NO"


  def provideTradingName(id: String): Unit = id match {
    case "valid"     => tradingNameTextField.value = "My Trading Name"
                        tradingName = "My Trading Name"
    case "invalid"   => tradingNameTextField.value = "£$£%^^"
    case "different" => tradingNameTextField.value = "Different Trading Name"
                        tradingName = "Different Trading Name"
    }


  def checkTextField(): Assertion = find(cssSelector("""input[type="text"][id="tradingName"]""")) shouldBe 'defined

  def checkRadioError(): Assertion = validateErrorMessages("tradingNameRadio", "error.tradename.empty")
  def checkInvalidError(): Assertion = validateErrorMessages("tradingName", "error.tradingName.invalid")
  def checkTradingNameEmptyError(): Assertion = validateErrorMessages("tradingName", "error.tradingName.empty")

}
