package uk.gov.hmrc.integration.cucumber.pages

import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage

object ApplicationSubmittedPage extends BasePage{

  override val url: String = s"$basePageUrl/submission-confirmation"

  def checkBodytitle(): Boolean = checkBodyTitle(getMessage("asp.body.header"))

}
