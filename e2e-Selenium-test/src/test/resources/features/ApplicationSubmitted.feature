#@vat
#
#  @AuthenticatedLogIn
#  Feature: Submission Confirmation
#
#    Scenario: Verify - Application submitted
#      Given I prime the application
#        |   enteredData             |    S4Llocation              |
#        | TAXABLE_NO                | vatChoice.taxableTurnoverChoice       |
#        | REGISTER_YES              | vatChoice.voluntaryChoice             |
#        | COMPANY_REGISTRATION_DATE | vatChoice.startDateChoice             |
#        | TRADING_NAME_NO           | vatTradingDetails.tradingNameChoice           |
#        | new@mail.com              | vatContact.email                |
#        | 07892736456               | vatContact.daytimePhone               |
#        | Test Business Description | sicAndCompliance.businessActivityDescription |
#        | COMPANY_BANK_ACCOUNT_NO   | vatFinancials.companyBankAccountChoice    |
#        | 45678                     | vatFinancials.estimateVatTurnover         |
#        | ZERO_RATED_SALES_NO       | vatFinancials.zeroRatedSalesChoice        |
#        | VAT_CHARGE_YES            | vatFinancials.vatChargeExpectancyChoice   |
#        | quarterly                 | vatFinancials.vatReturnFrequency          |
#        | MAR_JUN_SEP_DEC           | vatFinancials.accountingPeriod            |
#      Given   I am on the summary
#      When    I continue
#      Then    I will be prompted to application submitted page which displays application reference number