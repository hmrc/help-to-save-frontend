package uk.gov.hmrc.integration.cucumber.pages

import util.Random

object AuthorityWizardPage extends BasePage{

  override val url: String = baseAuthWizardPageUrl

  def goToAuthWizardPage(): Unit = {
    go to AuthorityWizardPage
    BasePage.waitForPageToBeLoaded(checkBodyTitle(getMessage("awp.body.header")), "Failed to load Authority Wizard page")

    deleteAllCookies()
    credId.value = getRandomCredentials

  }

  def credId: TextField = textField("authorityId")
  def redirectUrl: TextField = textField("redirectionUrl")
  def nino: TextField = textField("nino")
  def credentialStrength: SingleSel = singleSel("credentialStrength")
  def confidenceLevel: SingleSel = singleSel("confidenceLevel")
  def affinityGroup: SingleSel = singleSel("affinityGroup")
  def submit(): Unit = clickOn(cssSelector("input.button"))
  def presets: SingleSel = singleSel("presets-dropdown")

  private def checkTitle() : Boolean = {
    findById("content").getText shouldBe "Authority Wizard"
    findById("content").getText == "Authority Wizard"
  }

  def redirect =
  {
    redirectUrl.value = basePageUrl
  }

  def confidenceLevel(level : String) : Unit =
  {
    confidenceLevel.value = level
  }

  def credentialStrength(strength : String) : Unit =
  {
    credentialStrength.value = strength
  }

  def nino(num : String) : Unit =
  {
    nino.value = num
  }

  private def getRandomCredentials : String ={
    Random.nextInt(999999).toString
  }
}
