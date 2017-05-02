package uk.gov.hmrc.integration.cucumber.pages.sicAndCompliance.financial

import org.scalatest.Assertion
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage

object ManageAdditionalFundsCompliancePage extends BasePage {

  override val url:String = s"$basePageUrl/manages-funds-not-included-in-this-list"

  def checkBodyTitle(): Boolean = checkBodyTitle(getMessage("mafcp.body.header"))

  def manageAdditionalFundsRadioButton: RadioButtonGroup = radioButtonGroup("manageAdditionalFundsRadio")

  def clickManageAdditionalFundsOption(option: String): Unit = clickOptionYesNo(option, manageAdditionalFundsRadioButton, "true", "false")

  def checkManageAdditionalFundsOption(option: String): Assertion = checkOptionYesNo(option, manageAdditionalFundsRadioButton, "true", "false")

  def checkRadioError(): Assertion = validateErrorMessages("manageAdditionalFundsRadio", "error.manageAdditionalFundsCompliance.noSelection")

}