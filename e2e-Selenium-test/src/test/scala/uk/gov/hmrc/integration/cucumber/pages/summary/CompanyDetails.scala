package uk.gov.hmrc.integration.cucumber.pages.summary

import org.scalatest.Assertion
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.sicAndCompliance.BusinessActivityDescriptionPage
import uk.gov.hmrc.integration.cucumber.pages.vatFinancials.vatBankAccount.CompanyBankDetailsPage
import uk.gov.hmrc.integration.cucumber.pages.vatFinancials.{EstimateVatTurnoverPage, EstimateZeroRatedSalesPage}
import uk.gov.hmrc.integration.cucumber.pages.vatTradingDetails.TradingNamePage


trait CompanyDetails extends BasePage {

  def changeCompanyBankAccount(): Unit = click on id("companyDetails.companyBankAccountChangeLink")

  def verifyCompanyBankAccount(answer: String): Assertion =
    answer match{
      case "Yes" => findById("companyDetails.companyBankAccountAnswer").getText shouldBe "Yes"
      case "No" => findById("companyDetails.companyBankAccountAnswer").getText shouldBe "No"
    }

  def changeCompanyBankAccountName(): Unit = click on id("companyDetails.companyBankAccount.nameChangeLink")

  def changeCompanyBankAccountNumber(): Unit = click on id("companyDetails.companyBankAccount.numberChangeLink")

  def changeCompanySortCode(): Unit = click on id("companyDetails.companyBankAccount.sortCodeChangeLink")

  def verifyCompanyBankAccountDetails(): Assertion = {
    findById("companyDetails.companyBankAccount.nameAnswer").getText shouldBe CompanyBankDetailsPage.companyBankAccountName
    findById("companyDetails.companyBankAccount.numberAnswer").getText shouldBe "****" + CompanyBankDetailsPage.companyBankAccountNumber.substring(4)
    findById("companyDetails.companyBankAccount.sortCodeAnswer").getText shouldBe CompanyBankDetailsPage.sortCode
  }

  def verifyChangedCompanyBankAccountDetails(): Assertion = {
    findById("companyDetails.companyBankAccount.nameAnswer").getText shouldBe "MY NEW ACCOUNT"
    findById("companyDetails.companyBankAccount.numberAnswer").getText shouldBe "****3131"
    findById("companyDetails.companyBankAccount.sortCodeAnswer").getText shouldBe "12-12-19"
  }

  def changeTradingName(): Unit = click on id("vatDetails.tradingNameChangeLink")

  def verifyTradingName(): Unit = findById("vatDetails.tradingNameAnswer").getText shouldBe TradingNamePage.tradingName

  def changeBusinessActivityDescription(): Unit = click on id("companyDetails.businessActivity.descriptionChangeLink")

  def verifyBusinessActivityDescription(): Unit = findById("companyDetails.businessActivity.descriptionAnswer").getText shouldBe BusinessActivityDescriptionPage.businessDescription

  def changeEstimatedSalesValue(): Unit = click on id("companyDetails.estimatedSalesValueChangeLink")

  def verifyEstimatedSalesValue(): Unit = findById("companyDetails.estimatedSalesValueAnswer").getText shouldBe EstimateVatTurnoverPage.estimatedVatTurnoverValue

  def changeEstimatedZeroRatedSalesValue(): Unit = click on id("companyDetails.zeroRatedSalesValueChangeLink")

  def verifyEstimatedZeroRatedSalesValue(): Unit = findById("companyDetails.zeroRatedSalesValueAnswer").getText shouldBe EstimateZeroRatedSalesPage.estimatedZeroRatedSalesValue

  def changeZeroRatedSales(): Unit = click on id("companyDetails.zeroRatedSalesChangeLink")

  def verifyZeroRatedSales(answer: String): Assertion =
    answer match {
      case "Yes" => findById("companyDetails.zeroRatedSalesAnswer").getText shouldBe "Yes"
      case "No"  => findById("companyDetails.zeroRatedSalesAnswer").getText shouldBe "No"
    }

  def changeVatReturnExpectancy(): Unit = click on id("companyDetails.reclaimMoreVatChangeLink")

  def verifyVatReturnExpectancy(answer: String): Assertion =
    answer match {
      case "Yes" => findById("companyDetails.reclaimMoreVatAnswer").getText shouldBe "Yes, I expect to reclaim more VAT than I charge"
      case "No"  => findById("companyDetails.reclaimMoreVatAnswer").getText shouldBe "No, I expect to charge more VAT than I reclaim"
    }

  def changeAccountingPeriod(): Unit = click on id("companyDetails.accountingPeriodChangeLink")

  def verifyAccountingPeriodQuarterly(option: String): Assertion =
    option match {
      case "January" => findById("companyDetails.accountingPeriodAnswer").getText shouldBe "January, April, July and October"
      case "February"  => findById("companyDetails.accountingPeriodAnswer").getText shouldBe "February, May, August and November"
      case "March"  => findById("companyDetails.accountingPeriodAnswer").getText shouldBe "March, June, September and December"
    }

  def verifyAccountingPeriodMonthly(): Assertion = findById("companyDetails.accountingPeriodAnswer").getText shouldBe "Once a month"

  def changeEUGoods(): Unit = click on id("companyDetails.eori.euGoodsChangeLink")

  def verifyEUGoodsOption(goodsOption: String): Assertion = findById("companyDetails.eori.euGoodsAnswer").getText shouldBe goodsOption

  def verifyEoriDetails(eoriDetails: String): Assertion = findById("companyDetails.eoriAnswer").getText shouldBe eoriDetails

  def clickChangeEori(): Unit = click on id("companyDetails.eoriChangeLink")
}
