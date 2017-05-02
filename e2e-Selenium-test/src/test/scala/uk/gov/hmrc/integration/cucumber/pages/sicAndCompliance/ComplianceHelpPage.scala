package uk.gov.hmrc.integration.cucumber.pages.sicAndCompliance

import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage

object ComplianceHelpPage extends BasePage {

  override val url: String = s"$basePageUrl/compliance/compliance-help"

  def checkBodyTitle(): Boolean = checkBodyTitle(getMessage("chp.body.header"))


}
