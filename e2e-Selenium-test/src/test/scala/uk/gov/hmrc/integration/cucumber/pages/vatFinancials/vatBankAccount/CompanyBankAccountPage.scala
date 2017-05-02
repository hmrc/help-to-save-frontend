package uk.gov.hmrc.integration.cucumber.pages.vatFinancials.vatBankAccount

import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage

object CompanyBankAccountPage extends BasePage {

  override val url: String = s"$basePageUrl/company-bank-account"

  def checkBodyTitle(): Boolean = checkBodyTitle(getMessage("cbap.body.header"))

  def bankAccountRadio: RadioButtonGroup = radioButtonGroup("companyBankAccountRadio")

  def clickBankAccountOption(bankOption: String) =  bankOption match {
    case "Yes" => bankAccountRadio.value = "COMPANY_BANK_ACCOUNT_YES"
    case "No"  => bankAccountRadio.value = "COMPANY_BANK_ACCOUNT_NO"
  }

  def checkBankAccountOption(bankOption: String) =  bankOption match {
    case "Yes" => bankAccountRadio.value shouldBe "COMPANY_BANK_ACCOUNT_YES"
    case "No"  => bankAccountRadio.value shouldBe "COMPANY_BANK_ACCOUNT_NO"
  }

  def checkRadioError() = validateErrorMessages("companyBankAccountRadio", "error.companybankaccount.empty")

}
