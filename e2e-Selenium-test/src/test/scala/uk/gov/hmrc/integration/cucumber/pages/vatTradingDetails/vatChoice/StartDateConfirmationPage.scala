package uk.gov.hmrc.integration.cucumber.pages.vatTradingDetails.vatChoice

import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage


object StartDateConfirmationPage extends BasePage {

  override val url: String = s"$basePageUrl/start-date-confirmation"

  def checkBodyTitle(): Boolean = checkBodyTitle(getMessage("vsdap.body.header"))

}
