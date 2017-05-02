package uk.gov.hmrc.integration.cucumber.pages.vatTradingDetails.vatChoice

import org.scalatest.Assertion
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage

object VoluntaryRegistrationPage extends BasePage{

  override  val url: String = s"$basePageUrl/voluntary-registration"

  def voluntaryRegistrationRadioButton: RadioButtonGroup = radioButtonGroup("voluntaryRegistrationRadio")

  def checkBodyTitle(): Boolean = checkBodyTitle(getMessage("vrp.body.header"))

  def clickRegisterVoluntarily(): Unit = voluntaryRegistrationRadioButton.value = "REGISTER_YES"
  def clickNotToRegisterVoluntarily(): Unit = voluntaryRegistrationRadioButton.value = "REGISTER_NO"

  def checkRegisterVoluntarily(): Assertion = voluntaryRegistrationRadioButton.value shouldBe "REGISTER_YES"

  def radioErrorMessage: String = findById("voluntaryRegistrationRadio-error-summary").getText
  def errorMessage(): Assertion = radioErrorMessage shouldBe getMessage("error.registervoluntarily.empty")
}
