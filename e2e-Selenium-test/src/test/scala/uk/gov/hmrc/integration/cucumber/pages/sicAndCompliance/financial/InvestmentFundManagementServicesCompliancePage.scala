package uk.gov.hmrc.integration.cucumber.pages.sicAndCompliance.financial

import org.scalatest.Assertion
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage


object InvestmentFundManagementServicesCompliancePage extends BasePage {

  override val url:String = s"$basePageUrl/provides-investment-fund-management-services "

  def checkBodyTitle: Boolean = checkBodyTitle(getMessage("ifmscp.body.header"))

  def investmentFundManagementServicesComplianceRadioButton: RadioButtonGroup = radioButtonGroup("investmentFundManagementRadio")

  def clickInvestmentFundManagementServicesOption(option: String): Unit = clickOptionYesNo(option, investmentFundManagementServicesComplianceRadioButton, "true", "false")

  def checkInvestmentFundManagementServicesOption(option: String): Assertion = checkOptionYesNo(option, investmentFundManagementServicesComplianceRadioButton, "true", "false")

  def checkRadioError(): Assertion = validateErrorMessages("investmentFundManagementRadio", "error.investmentFundManagementServicesCompliance.noSelection")
}
