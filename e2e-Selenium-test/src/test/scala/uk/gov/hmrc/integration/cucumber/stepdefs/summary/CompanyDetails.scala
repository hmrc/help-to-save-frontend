package uk.gov.hmrc.integration.cucumber.stepdefs.summary

import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.summary.SummaryPage

trait CompanyDetails extends BasePage {

  // Business Activity Description

  Then("""^the summary will display the description on the business activity description question$""") { () =>
    SummaryPage.verifyBusinessActivityDescription()
  }

  And("""^I select 'Change' on the Business Activity Description$""") { () =>
    SummaryPage.changeBusinessActivityDescription()
  }

  // Company Contact Details
  Then("""^the summary will display the email address, daytime phone number, mobile number and website address on respective question$"""){ ()=>
    SummaryPage.verifyCompanyContactDetails()
  }

  When("""^I click change on '(.*)' in summary$"""){ (clickOption:String)=>
    // SummaryPage.changeBuisnessEmailAddress()
    SummaryPage.clickChangeCompanyContactOption(clickOption)
  }

  When("""^I click change on business daytime phone number$"""){ ()=>
    SummaryPage.changeBusinessDaytimePhone()
  }

  When("""^I click change on business mobile number$"""){ ()=>
    SummaryPage.changeBusinessMobileNumber()
  }

  When("""^I click change on business website address$"""){ ()=>
    SummaryPage.changeBusinessWebsiteAddress()
  }

  Then("""^the summary will display the updated '(.*)' in 'Company contact details' section$"""){ (updatedChange: String)=>
      // SummaryPage.verifyChangedBusinessEmailAddress()
    SummaryPage.verifyUpdatedCompanyContactOption(updatedChange)
  }

  Then("""^the summary will display the updated daytime phone number in 'Company contact details' section$"""){ () =>
    SummaryPage.verifyChangedBusinessDaytimePhone()
  }


  // Bank Account

  And("""^I select 'Change' on the Bank Account$"""){ () =>
    SummaryPage.changeCompanyBankAccount()
  }

  Then("""^the summary will display '(.*)' on the company bank account question$"""){ (answer: String) =>
    SummaryPage.verifyCompanyBankAccount(answer)
  }

  // Bank Account Details

  Then("""^the summary will display the account name, number and sort code on respective question$"""){ () =>
    SummaryPage.verifyCompanyBankAccountDetails()
  }

  Then("""^the summary will display the changed account name, number and sort code on respective question$""") { () =>
    SummaryPage.verifyChangedCompanyBankAccountDetails()
  }

  And("""^I select 'Change' on the Account Name$"""){ () =>
    SummaryPage.changeCompanyBankAccountName()
  }

  And("""^I select 'Change' on the Account Number$"""){ () =>
    SummaryPage.changeCompanyBankAccountNumber()
  }

  And("""^I select 'Change' on the Sort Code"""){ () =>
    SummaryPage.changeCompanySortCode()
  }

  // Estimated VAT taxable turnover
  Then("""^I verify the estimated sales value in summary page$"""){ () =>
    SummaryPage.verifyEstimatedSalesValue()
  }

  And("""^the summary will display the value on the estimated vat turnover question$"""){ () =>
    SummaryPage.verifyEstimatedSalesValue()
  }

  When("""^I click change on the estimated VAT taxable turnover$"""){ () =>
    SummaryPage.changeEstimatedSalesValue()
  }

  // Zero Rated Sales
  And("""^the summary will display '(.*)' on the zero-rated sales answer$"""){ (answer: String) =>
    SummaryPage.verifyZeroRatedSales(answer)
  }

  When("""^I select 'Change' on the zero-rated sales$"""){ () =>
    SummaryPage.changeZeroRatedSales()
  }

  // Estimated Zero Rated Sales
  Then("""^I verify the estimated zero-rated sales value in summary page$"""){ () =>
    SummaryPage.verifyEstimatedSalesValue()
  }

  And("""^the summary will display the value on the estimated zero-rated sales question$"""){ () =>
    SummaryPage.verifyEstimatedZeroRatedSalesValue()
  }

  When("""^I click change on the estimated zero-rated sales$"""){ () =>
    SummaryPage.changeEstimatedZeroRatedSalesValue()
  }

  // Vat Charge Expectancy
  And("""^the summary will display '(.*)' for reclaim more vat question$"""){ (answer: String) =>
    SummaryPage.verifyVatReturnExpectancy(answer)
  }

  And("""^I click change on the reclaim more vat$"""){ () =>
    SummaryPage.changeVatReturnExpectancy()
  }

  //Vat Return Frequency
  And("""^I click change on the accounting period$"""){ () =>
    SummaryPage.changeAccountingPeriod()
  }

  // Accounting Period
  Then("""^the summary will display the return period starting (.*) for accounting period question$"""){ (option: String) =>
    goToSummaryPage()
    SummaryPage.verifyAccountingPeriodQuarterly(option)
  }

  Then("""^the summary will display the return period as monthly for accounting period question$"""){ () =>
    SummaryPage.verifyAccountingPeriodMonthly()
  }

  // EU Goods
  Then("""^the summary will display '(.*)' on the EU Goods question$"""){ (euGoods: String) =>
    SummaryPage.verifyEUGoodsOption(euGoods)
  }

  Then("""^I verify EU goods details as '(.*)' and will not see eori apply details in summary page$"""){ (euGoods: String) =>
    SummaryPage.verifyEUGoodsOption(euGoods)
  }

  And("""^I click change on the 'Trades goods or services with countries outside the EU' in summary$"""){ () =>
    SummaryPage.changeEUGoods()
  }

  // Apply EORI Number
  Then("""^the summary will display the Economic Operator Registration and Identification number as (.*)$"""){ (eoriSummary: String) =>
    SummaryPage.verifyEoriDetails(eoriSummary)
  }

  Then("""^I verify eori details as '(.*)' in summary page$""") { (applyEori: String) =>
    SummaryPage.verifyEoriDetails(applyEori)
  }

  And("""^I click change on the eori details$"""){ () =>
    SummaryPage.clickChangeEori()
  }
}
