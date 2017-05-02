package uk.gov.hmrc.integration.cucumber.pages.vatTradingDetails.vatEUTrading

import org.scalatest.Assertion
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage

object EUGoodsPage extends BasePage {

  override val url: String = s"$basePageUrl/eu-goods"

  def checkBodyTitle(): Boolean = checkBodyTitle(getMessage("eugp.body.header"))

  def euGoodsRadio: RadioButtonGroup = radioButtonGroup("euGoodsRadio")

  def clickEUGoodsOption(option: String): Unit =  option match {
    case "Yes" => euGoodsRadio.value = "EU_GOODS_YES"
    case "No"  => euGoodsRadio.value = "EU_GOODS_NO"
  }

  def checkEUGoodsOption(option: String): Assertion =  option match {
    case "Yes" => euGoodsRadio.value shouldBe "EU_GOODS_YES"
    case "No"  => euGoodsRadio.value shouldBe "EU_GOODS_NO"
  }

  def checkRadioError(): Assertion = validateErrorMessages("euGoodsRadio", "error.euGoods.noSelection")

}
