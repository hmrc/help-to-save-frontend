package uk.gov.hmrc.integration.cucumber.pages

import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage


object BeforeYouRegisterPage extends BasePage {

  override val url: String = s"$basePageUrl/start"

  def checkBodyTitle(): Boolean = checkBodyTitle(getMessage("start.body.header"))

  def verifyVatLinks() : Unit = {
    def summary(id: Int) = s"details [aria-controls='details-content-$id'] span.summary"
    def content(id: Int) = s"details [aria-controls='details-content-$id'][aria-expanded='true']"

    for (idx <- 0 to 2) {
      clickOn(cssSelector(summary(idx)))
      find(cssSelector(content(idx))) shouldBe 'defined
    }
  }
}
