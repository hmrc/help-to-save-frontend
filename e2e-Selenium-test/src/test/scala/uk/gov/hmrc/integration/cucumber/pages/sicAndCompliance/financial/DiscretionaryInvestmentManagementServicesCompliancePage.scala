package uk.gov.hmrc.integration.cucumber.pages.sicAndCompliance.financial

import org.scalatest.Assertion
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage

object DiscretionaryInvestmentManagementServicesCompliancePage extends BasePage{

  override val url:String = s"$basePageUrl/provides-discretionary-investment-management-services"

  def checkBodyTitle: Boolean = checkBodyTitle(getMessage("dimscp.body.header"))

  def discretionaryInvestmentManagementServicesComplianceRadioButton: RadioButtonGroup = radioButtonGroup("discretionaryInvestmentManagementServicesRadio")

  def clickDiscretionaryInvestmentManagementServicesOption(option: String): Unit = clickOptionYesNo(option, discretionaryInvestmentManagementServicesComplianceRadioButton, "true", "false")

  def checkDiscretionaryInvestmentManagementServicesOption(option: String): Unit = checkOptionYesNo(option, discretionaryInvestmentManagementServicesComplianceRadioButton, "true", "false")

  def checkRadioError(): Assertion = validateErrorMessages("discretionaryInvestmentManagementServicesRadio", "error.discretionaryInvestmentManagementServicesCompliance.noSelection")
}
