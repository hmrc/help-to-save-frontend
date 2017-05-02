#@smoke
#@BrowserStack
#
#Feature: Smoke Test
# #Needs to be updated after every sprint to cover the added pages
#  Scenario: The user navigates successfully through the application
#    Given I am an authenticated user
#    When  I attempt to access a VAT registration page directly
#    Then  I will be taken to the start page
#    And   I continue
#    Then  I will be presented with the >83K question
#    And   I select the option 'No' for the taxable turnover
#    And   I save and continue
#    Then  I will be presented with the 'Do you want to register voluntarily for VAT' page
#    And   I select the option 'Yes' for the voluntary registration
#    And   I save and continue
#    Then  I will be presented with the voluntary registration reason page
#    And   I indicate that 'the company already sells...'
#    And   I save and continue
#    Then  I will be presented with the Start Date page
#    And   I provide a date which is 15 days in the future
#    And   I save and continue
#    Then  I will be presented with the Trading Name page
#    And   I select the option 'Yes' for the trading name
#    And   I enter a trading name which is valid
#    And   I save and continue
#    Then  I will be presented with the business contact details page
#    And   I provide the email address, daytime phone number, mobile number and website address on business contact page
#    And   I save and continue
#    Then  I will be presented with the EU goods page
#    And   I select the option 'Yes' for the EU Goods
#    And   I save and continue
#    Then  I will be presented with the apply eori page
#    And   I select 'Yes' in the apply eori page
#    And   I save and continue
#    Then  I will be presented with the business activity description page
#    And   I provide the 'valid' business activity description
#    And   I save and continue
#    # Sic code and Financial Compliances
#    Then  I will be prompted to provide up to 4, 8 digits standard industry codes in stub page
#    And   I submit after providing financial-compliance sic codes
#    Then  I will be informed that they are going to have to answer some additional questions
#    And   I continue
#    Then  I am presented with the advice or consultancy page
#    And   I select 'Yes' on the advice or consultancy page
#    And   I continue
#    Then  I am presented with the intermediary page
#    And   I select 'No' on the intermediary page
#    And   I continue
#    Then  I am presented with the charge fees compliance page
#    And   I select Yes in charge fees compliance page
#    And   I continue
#    Then  I will be presented with the additional non securities work compliance page
#    And   I select 'No' on the additional non securities work compliance page
#    And   I save and continue
#    Then  I will be presented with the discretionary investment management service compliance page
#    And   I select 'No' on the discretionary investment management service compliance page
#    And   I save and continue
#    Then  I will be presented with the vehicle or equipment leasing compliance page
#    And   I select 'No' on the leasing vehicles or equipment page
#    And   I save and continue
#    Then  I will be presented with the investment fund management service compliance page
#    And   I select 'Yes' on the investment fund management service compliance page
#    And   I save and continue
#    Then  I will be presented with the manage funds additional compliance page
#    And   I select Yes in manage additional funds compliance page
#    And   I save and continue
#    # FC - end
#    Then  I will be presented with the company bank account page
#    And   I select the option 'Yes' for the company bank account
#    And   I save and continue
#    Then  I will be presented with the company bank details page
#    And   I provide the company bank account details
#    And   I save and continue
#    Then  I will be presented with estimated VAT Taxable Turnover page
#    And   I provide an estimated vat turnover value
#    And   I save and continue
#    Then  I will be presented with the zero rated sales page
#    And   I select 'Yes' on the zero rated sales page
#    And   I save and continue
#    Then  I will be presented with the estimate zero-rated sales page
#    And   I enter a valid value and I click 'Save and continue' on the estimated zero-rated sales page
#    Then  I am presented with the vat return frequency page
#    And   I indicate I would like to submit quarterly VAT returns
#    And   I save and continue
#    Then  I will be presented with the accounting period page
#    And   I indicate I would want vat return period starting March
#    And   I save and continue
#    Then  I will be presented with the summary
#
#
