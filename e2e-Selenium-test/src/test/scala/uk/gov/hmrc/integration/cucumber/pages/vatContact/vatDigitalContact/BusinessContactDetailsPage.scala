package uk.gov.hmrc.integration.cucumber.pages.vatContact.vatDigitalContact

import com.mifmif.common.regex.Generex
import org.scalatest.Assertion
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.utils.RandomUtils

object BusinessContactDetailsPage extends BasePage {

  override val url: String = s"$basePageUrl/business-contact"

  def checkBodyTitle(): Boolean = checkBodyTitle(getMessage("bcdp.body.header"))

  def emailAddressField: TextField = textField("email")

  def daytimePhoneNoField: TextField = textField("daytimePhone")

  def mobileNoField: TextField = textField("mobile")

  def webAddressField: TextField =textField("website")

  var emailAddress: String = ""
  var daytimePhoneNo: String = ""
  var mobileNo: String = ""
  var webAddress: String = ""

  def initialise(): Unit = {
    emailAddress = ""
    daytimePhoneNo = ""
    mobileNo = ""
    webAddress = ""
  }

  def provideEmailAddress(): Unit = {
    emailAddress = RandomUtils.randString(7) + "@address.com"
    emailAddressField.value = emailAddress
  }

  def provideDaytimePhoneNo(): Unit = {
    daytimePhoneNo = RandomUtils.randNumbers(11)
    daytimePhoneNoField.value = daytimePhoneNo
  }

  def provideMobileNo(): Unit = {
    mobileNo = RandomUtils.randNumbers(11)
    mobileNoField.value = mobileNo
  }

  def provideWebAddress(): Unit = {
    webAddress = "www." + RandomUtils.randString(5) + ".com"
    webAddressField.value = webAddress
  }

  def provideInvalidCharacters(): Unit = {
    emailAddressField.value = emailValidationRegex.random(1, 20) + "@" + emailValidationRegex.random(1, 20)
    daytimePhoneNoField.value = phoneNoValidationRegex.random(1,20)
    mobileNoField.value = phoneNoValidationRegex.random(1,20)
  }


  val emailValidationRegex = new Generex("[,!@#$%^&*();\\/|<>\"']")
  val phoneNoValidationRegex = new Generex("([a_+-.,!@#$%^&*();\\/|<>\"'])")

  def changeEmailAddress(): Unit = {
    emailAddress = "change" + "@address.com"
    emailAddressField.value = emailAddress
  }

  def changeDaytimePhoneNo(): Unit = {
    daytimePhoneNo = RandomUtils.randNumbers(11)
    daytimePhoneNoField.value = daytimePhoneNo
  }

  def changeMobileNo(): Unit = {
    mobileNo = RandomUtils.randNumbers(11)
    mobileNoField.value = mobileNo
  }

  def changeWebAddress(): Unit = {
    webAddress = "www." + "changeweb" + ".com"
    webAddressField.value = webAddress
  }

  def checkEmailAddress(): Unit = emailAddressField.value shouldBe emailAddress

  def checkDaytimePhoneNo(): Unit = daytimePhoneNoField.value shouldBe daytimePhoneNo

  def checkMobileNo(): Unit = mobileNoField.value shouldBe mobileNo

  def checkWebAddress(): Unit = webAddressField.value shouldBe webAddress

  def checkEmailAddressEmptyError(): Assertion = validateErrorMessages("email", "error.enterEmailAddress")

  def checkEmailAddressInvalidError(): Assertion = validateErrorMessages("email", "error.enterValidEmailAddress")

  def checkDaytimePhoneInvalidError(): Assertion = validateErrorMessages("daytimePhone", "error.enterValidDaytimeNumber")

  def checkMobileNoInvalidError(): Assertion = validateErrorMessages("mobile", "error.enterValidMobileNumber")

  def checkBusinessContactEmptyError(): Assertion = validateErrorMessages("daytimePhone", "error.enterBusinessContact")

  def checkPrePopulateCompanyContactOption(option: String): Assertion = option match {
    case "Business email address" => emailAddressField.value shouldBe emailAddress
    case "Business daytime phone number" => daytimePhoneNoField.value shouldBe daytimePhoneNo
    case "Business mobile number" => mobileNoField.value shouldBe mobileNo
    case "Business website address" => webAddressField.value shouldBe webAddress
  }

  def provideDifferentCompanyContactOption(option: String):Unit = option match {
    case "Business email address" => changeEmailAddress()
    case "Business daytime phone number" => changeDaytimePhoneNo()
    case "Business mobile number" => changeMobileNo()
    case "Business website address" => changeWebAddress()
  }

}
