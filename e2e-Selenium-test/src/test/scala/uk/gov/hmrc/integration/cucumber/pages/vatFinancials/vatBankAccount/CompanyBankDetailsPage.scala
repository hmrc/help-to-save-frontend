package uk.gov.hmrc.integration.cucumber.pages.vatFinancials.vatBankAccount

import org.scalatest.Assertion
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage

object CompanyBankDetailsPage extends BasePage {
  // ToDo Check the urls and labels once the page has been completed

  override val url: String = s"$basePageUrl/bank-details"

  def checkBodyTitle(): Boolean = checkBodyTitle(getMessage("bdp.body.header"))

  def companyBankAccountNameField: TextField = textField("accountName")

  def companyBankAccountNumberField: TextField = textField("accountNumber")

  def sortCodeFirstPairField: TextField = textField("sortCode.part1")

  def sortCodeSecondPairField: TextField = textField("sortCode.part2")

  def sortCodeThirdPairField: TextField = textField("sortCode.part3")

  var companyBankAccountName: String = "My Account"
  var companyBankAccountNumber: String = "12345678"
  var sortCode: String = "12-12-19"

  var companyChangeBankAccountName: String = "My New Account"
  var companyChangeBankAccountNumber: String = "13133131"
  var changeSortCode: String = "12-12-19"

  def initialise(): Unit = {
    companyBankAccountName = "My Account"
    companyBankAccountNumber = "12345678"
    sortCode = "12-12-19"
  }

  def provideCompanyBankAccountDetails(): Unit = {
    companyBankAccountNameField.value  = "MY ACCOUNT"
    companyBankAccountNumberField.value = "19121992"
    sortCodeFirstPairField.value = "13"
    sortCodeSecondPairField.value = "31"
    sortCodeThirdPairField.value = "19"

     companyBankAccountName = "MY ACCOUNT"
     companyBankAccountNumber = "19121992"
     sortCode = "13-31-19"
  }

  def provideDifferentCompanyBankAccountDetails(): Unit = {
    companyBankAccountNameField.value  = "MY NEW ACCOUNT"
    companyBankAccountNumberField.value = "13133131"
    sortCodeFirstPairField.value = "12"
    sortCodeSecondPairField.value = "12"
    sortCodeThirdPairField.value = "19"
  }

  def checkCompanyBankAccountDetails(): Assertion = {
    companyBankAccountNameField.value shouldBe companyBankAccountName
    companyBankAccountNumberField.value shouldBe 'empty
  }

  val invalidCharacters = List("i", "n", ";", "|", "[", "@", "!", "£")
  def fields = List(
    companyBankAccountNumberField,
    sortCodeFirstPairField,
    sortCodeSecondPairField,
    sortCodeThirdPairField
  )

  //Todo Remove one of the invalid char function
  def enterInvalidCharacters(): Unit = {
    invalidCharacters.foreach {
      case (char) =>
        fields.foreach(field => {
          field.value = char
          field.value shouldBe 'empty
        })
    }
  }

  def enterInvalidCharacters2(): Unit = {
    for (char <- invalidCharacters;
         field <- fields) {
      field.value = char
      field.value shouldBe 'empty
    }
  }

  def checkCompanyBankAccountNameError(): Assertion = validateErrorMessages("accountName", "error.enterAccountName")

  def checkCompanyBankAccountNumberError(): Assertion = validateErrorMessages("accountNumber", "error.enterAccountNumber")

  def checkSortCodeError(): Assertion = validateErrorMessages("sortCode", "error.enterSortCode")

}
