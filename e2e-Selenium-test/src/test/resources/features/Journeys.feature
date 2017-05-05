#@Suite
#Feature: Journeys
#
#  This feature tests the application's routing, ensuring that users are directed to the right place
#  depending on the answers they give.
#
#  Background:
#    Given I am on the Date of Death page
#
#  Scenario: 1 - Entering a date of death before 06 April 2017 tells the user they cannot use the calculator
#    When I enter 05/04/2017 as the date of death
#    Then I should be on the No Threshold Increase page
#
#  Scenario: 2 - Answering No to the Part of Estate Passing to Direct Descendants question tells the user they cannot use the calculator
#    When I enter 06/04/2017 as the date of death
#    And answer No to the Part of Estate Passing to Direct Descendants question
#    Then I should be on the No Threshold Increase page
#
#  Scenario: 3 - Entering a date property was changed before 8 July 2015 tells the user they cannot use the calculator
#    When I enter 07/07/2020 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £500,000 as the Value of Estate
#    And £400,000 as the Chargeable Estate Value
#    And answer Yes to the Property In Estate question
#    And enter £300,000 as the property value
#    And answer Yes, some of it passed to Property Passing To Direct Descendants
#    And enter 50% as the Percentage Passed To Direct Descendants
#    And answer No to the Exemptions And Relief Claimed question
#    And answer No to the Transfer Any Unused Threshold question
#    And answer Yes to the Claim Downsizing Threshold question
#    And enter 07/07/2015 as the Date Property Was Changed
#    Then I should be on the No Downsizing Threshold Increase page
#
#  Scenario: 4 - Answering Yes to the Does Grossing Up Apply To Residence question tells the user they cannot use the calculator
#    When I enter 01/09/2020 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £500,000 as the Value of Estate
#    And £400,000 as the Chargeable Estate Value
#    And answer Yes to the Property In Estate question
#    And enter £300,000 as the property value
#    And answer Yes, some of it passed to Property Passing To Direct Descendants
#    And enter 50% as the Percentage Passed To Direct Descendants
#    And answer Yes to the Exemptions And Relief Claimed question
#    And answer Yes to the Grossing Up On Estate Property question
#    Then I should be on the Unable To Calculate Threshold Increase page
#
#  Scenario: 5 - Answering Yes to the Grossing Up On Estate Assets question tells the user they cannot use the calculator
#    When I enter 01/09/2020 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £500,000 as the Value of Estate
#    And £400,000 as the Chargeable Estate Value
#    And answer No to the Property In Estate question
#    And click the Continue link on the No Additional Threshold Available page
#    And answer No to the Transfer Any Unused Threshold question
#    And answer Yes to the Claim Downsizing Threshold question
#    And enter 01/10/2018 as the Date Property Was Changed
#    And enter £285,000 as the Value Of Changed Property
#    And answer Yes to the Assets Passing To Direct Descendants question
#    And answer Yes to the Grossing Up On Estate Assets question
#    Then I should be on the Unable To Calculate Threshold Increase page
#
#  Scenario: 6 - Journey answering No where possible
#    When I enter 01/09/2020 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £500,000 as the Value of Estate
#    And £400,000 as the Chargeable Estate Value
#    And answer No to the Property In Estate question
#    And click the Continue link on the No Additional Threshold Available page
#    And answer No to the Transfer Any Unused Threshold question
#    And answer No to the Claim Downsizing Threshold question
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £0
#    When I click the Show previous answers reveal
#    And the following answers
#      | Label                                                                                  | Value            |
#      | What was the date of death?                                                            | 1 September 2020 |
#      | Does any of the estate pass to the deceased’s children or other direct descendants?    | Yes              |
#      | What is the total value of the estate before deducting any reliefs or exemptions?      | £500,000         |
#      | What is the amount of the total chargeable estate on death?                            | £400,000         |
#      | Does the estate include any residential property that the deceased owned and lived in? | No               |
#      | Do you wish to transfer any unused residence nil rate band?                            | No               |
#      | Are you claiming any downsizing allowance?                                             | No               |
#
#  Scenario: 7 - Journey answering Yes to Property In Estate but no to everything else
#    When I enter 01/09/2020 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £500,000 as the Value of Estate
#    And £400,000 as the Chargeable Estate Value
#    And answer Yes to the Property In Estate question
#    And enter £300,000 as the property value
#    And answer No to Property Passing To Direct Descendants
#    And click the Continue link on the No Additional Threshold Available page
#    And answer No to the Transfer Any Unused Threshold question
#    And answer No to the Claim Downsizing Threshold question
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £0
#    When I click the Show previous answers reveal
#    And the following answers
#      | Label                                                                                        | Value            |
#      | What was the date of death?                                                                  | 1 September 2020 |
#      | Does any of the estate pass to the deceased’s children or other direct descendants?          | Yes              |
#      | What is the total value of the estate before deducting any reliefs or exemptions?            | £500,000         |
#      | What is the amount of the total chargeable estate on death?                                  | £400,000         |
#      | Does the estate include any residential property that the deceased owned and lived in?       | Yes              |
#      | What is the value of the residence at the date of death?                                     | £300,000         |
#      | Did any part of this residence pass to a direct descendant or their spouse or civil partner? | No               |
#      | Do you wish to transfer any unused residence nil rate band?                                  | No               |
#      | Are you claiming any downsizing allowance?                                                   | No               |
#
#  Scenario: 8 - Journey answering Yes to Property In Estate and a value for Percentage Passed To Direct Descendants but no to everything else
#    When I enter 01/09/2020 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £500,000 as the Value of Estate
#    And £400,000 as the Chargeable Estate Value
#    And answer Yes to the Property In Estate question
#    And enter £300,000 as the property value
#    And answer Yes, some of it passed to Property Passing To Direct Descendants
#    And enter 50% as the Percentage Passed To Direct Descendants
#    And answer No to the Exemptions And Relief Claimed question
#    And answer No to the Transfer Any Unused Threshold question
#    And answer No to the Claim Downsizing Threshold question
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £150,000
#    When I click the Show previous answers reveal
#    And the following answers
#      | Label                                                                                                                 | Value                  |
#      | What was the date of death?                                                                                           | 1 September 2020       |
#      | Does any of the estate pass to the deceased’s children or other direct descendants?                                   | Yes                    |
#      | What is the total value of the estate before deducting any reliefs or exemptions?                                     | £500,000               |
#      | What is the amount of the total chargeable estate on death?                                                           | £400,000               |
#      | Does the estate include any residential property that the deceased owned and lived in?                                | Yes                    |
#      | What is the value of the residence at the date of death?                                                              | £300,000               |
#      | Did any part of this residence pass to a direct descendant or their spouse or civil partner?                          | Yes, some of it passed |
#      | What percentage of the residence passes to direct descendants following the deceased’s death?                         | 50%                    |
#      | Is any part of the residence exempt from Inheritance Tax, or does any part of it qualify for any relief on the death? | No                     |
#      | Do you wish to transfer any unused residence nil rate band?                                                           | No                     |
#      | Are you claiming any downsizing allowance?                                                                            | No                     |
#
#  Scenario: 9 - Journey answering Yes to Property In Estate and Exemptions but no to everything else
#    When I enter 01/09/2020 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £500,000 as the Value of Estate
#    And £400,000 as the Chargeable Estate Value
#    And answer Yes to the Property In Estate question
#    And enter £300,000 as the property value
#    And I answer Yes, some of it passed to Property Passing To Direct Descendants
#    And enter 50% as the Percentage Passed To Direct Descendants
#    And answer Yes to the Exemptions And Relief Claimed question
#    And answer No to the Grossing Up On Estate Property question
#    And enter £250,000 as the Chargeable Property Value
#    And enter £200,000 as the Chargeable Inherited Property Value
#    And answer No to the Transfer Any Unused Threshold question
#    And answer No to the Claim Downsizing Threshold question
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £175,000
#    When I click the Show previous answers reveal
#    And the following answers
#      | Label                                                                                                                 | Value                  |
#      | What was the date of death?                                                                                           | 1 September 2020       |
#      | Does any of the estate pass to the deceased’s children or other direct descendants?                                   | Yes                    |
#      | What is the total value of the estate before deducting any reliefs or exemptions?                                     | £500,000               |
#      | What is the amount of the total chargeable estate on death?                                                           | £400,000               |
#      | Does the estate include any residential property that the deceased owned and lived in?                                | Yes                    |
#      | What is the value of the residence at the date of death?                                                              | £300,000               |
#      | Did any part of this residence pass to a direct descendant or their spouse or civil partner?                          | Yes, some of it passed |
#      | What percentage of the residence passes to direct descendants following the deceased’s death?                         | 50%                    |
#      | Is any part of the residence exempt from Inheritance Tax, or does any part of it qualify for any relief on the death? | Yes                    |
#      | Does grossing-up or interaction apply?                                                                                | No                     |
#      | What is the chargeable value of the residence for Inheritance Tax purposes?                                           | £250,000               |
#      | What is the chargeable value of the residence that passes to direct descendants?                                      | £200,000               |
#      | Do you wish to transfer any unused residence nil rate band?                                                           | No                     |
#      | Are you claiming any downsizing allowance?                                                                            | No                     |
#
#  Scenario: 10 - Journey answering Yes to Value Being Transferred but no to everything else
#    When I enter 01/09/2020 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £500,000 as the Value of Estate
#    And £400,000 as the Chargeable Estate Value
#    And answer No to the Property In Estate question
#    And click the Continue link on the No Additional Threshold Available page
#    And answer Yes to the Transfer Any Unused Threshold question
#    And enter £175,000 as the Value Being Transferred
#    And answer No to the Claim Downsizing Threshold question
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £0
#    When I click the Show previous answers reveal
#    And the following answers
#      | Label                                                                                  | Value            |
#      | What was the date of death?                                                            | 1 September 2020 |
#      | Does any of the estate pass to the deceased’s children or other direct descendants?    | Yes              |
#      | What is the total value of the estate before deducting any reliefs or exemptions?      | £500,000         |
#      | What is the amount of the total chargeable estate on death?                            | £400,000         |
#      | Does the estate include any residential property that the deceased owned and lived in? | No               |
#      | Do you wish to transfer any unused residence nil rate band?                            | Yes              |
#      | How much transferable residence nil rate band are you claiming?                        | £175,000         |
#      | Are you claiming any downsizing allowance?                                             | No               |
#
#  Scenario: 11 - Journey answering Yes to Downsizing but no to everything else
#    When I enter 01/09/2020 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £500,000 as the Value of Estate
#    And £400,000 as the Chargeable Estate Value
#    And answer No to the Property In Estate question
#    And click the Continue link on the No Additional Threshold Available page
#    And answer No to the Transfer Any Unused Threshold question
#    And answer Yes to the Claim Downsizing Threshold question
#    And enter 01/10/2018 as the Date Property Was Changed
#    And enter £285,000 as the Value Of Changed Property
#    And answer No to the Assets Passing To Direct Descendants question
#    Then I should be on the No Downsizing Threshold Increase page
#    When I click the Continue link
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £0
#    When I click the Show previous answers reveal
#    And the following answers
#      | Label                                                                                                                                                    | Value            |
#      | What was the date of death?                                                                                                                              | 1 September 2020 |
#      | Does any of the estate pass to the deceased’s children or other direct descendants?                                                                      | Yes              |
#      | What is the total value of the estate before deducting any reliefs or exemptions?                                                                        | £500,000         |
#      | What is the amount of the total chargeable estate on death?                                                                                              | £400,000         |
#      | Does the estate include any residential property that the deceased owned and lived in?                                                                   | No               |
#      | Do you wish to transfer any unused residence nil rate band?                                                                                              | No               |
#      | Are you claiming any downsizing allowance?                                                                                                               | Yes              |
#      | What was the date the property was disposed of?                                                                                                          | 1 October 2018   |
#      | What was the value of the deceased’s interest in the property at the date it was disposed of?                                                            | £285,000         |
#      | Other than the property that you are claiming residence nil rate band allowance on, do any other assets pass to a direct descendant following the death? | No               |
#
#  Scenario: 12 - Journey answering Yes to Downsizing and Assets Passing to Direct Descendant but no to everything else
#    When I enter 01/09/2020 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £500,000 as the Value of Estate
#    And £400,000 as the Chargeable Estate Value
#    And answer No to the Property In Estate question
#    And click the Continue link on the No Additional Threshold Available page
#    And answer No to the Transfer Any Unused Threshold question
#    And answer Yes to the Claim Downsizing Threshold question
#    And enter 01/10/2018 as the Date Property Was Changed
#    And enter £285,000 as the Value Of Changed Property
#    And answer Yes to the Assets Passing To Direct Descendants question
#    And answer No to the Grossing Up On Estate Assets question
#    And enter £250,000 as the Value Of Assets Passing
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £175,000
#    When I click the Show previous answers reveal
#    And the following answers
#      | Label                                                                                                                                                    | Value            |
#      | What was the date of death?                                                                                                                              | 1 September 2020 |
#      | Does any of the estate pass to the deceased’s children or other direct descendants?                                                                      | Yes              |
#      | What is the total value of the estate before deducting any reliefs or exemptions?                                                                        | £500,000         |
#      | What is the amount of the total chargeable estate on death?                                                                                              | £400,000         |
#      | Does the estate include any residential property that the deceased owned and lived in?                                                                   | No               |
#      | Do you wish to transfer any unused residence nil rate band?                                                                                              | No               |
#      | Are you claiming any downsizing allowance?                                                                                                               | Yes              |
#      | What was the date the property was disposed of?                                                                                                          | 1 October 2018   |
#      | What was the value of the deceased’s interest in the property at the date it was disposed of?                                                            | £285,000         |
#      | Other than the property that you are claiming residence nil rate band allowance on, do any other assets pass to a direct descendant following the death? | Yes              |
#      | Does grossing-up or interaction apply?                                                                                                                   | No               |
#      | What is the total value of the other assets passing to direct descendants?                                                                               | £250,000         |
#
#  Scenario: 13 - Journey answering Yes to Downsizing, Assets Passing to Direct Descendant and Value Available When Property Changed but no to everything else
#    When I enter 01/09/2020 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £500,000 as the Value of Estate
#    And £400,000 as the Chargeable Estate Value
#    And answer No to the Property In Estate question
#    And click the Continue link on the No Additional Threshold Available page
#    And answer No to the Transfer Any Unused Threshold question
#    And answer Yes to the Claim Downsizing Threshold question
#    And enter 01/10/2018 as the Date Property Was Changed
#    And enter £285,000 as the Value Of Changed Property
#    And answer Yes to the Assets Passing To Direct Descendants question
#    And answer No to the Grossing Up On Estate Assets question
#    And enter £250,000 as the Value Of Assets Passing
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £175,000
#    When I click the Show previous answers reveal
#    And the following answers
#      | Label                                                                                                                                                    | Value            |
#      | What was the date of death?                                                                                                                              | 1 September 2020 |
#      | Does any of the estate pass to the deceased’s children or other direct descendants?                                                                      | Yes              |
#      | What is the total value of the estate before deducting any reliefs or exemptions?                                                                        | £500,000         |
#      | What is the amount of the total chargeable estate on death?                                                                                              | £400,000         |
#      | Does the estate include any residential property that the deceased owned and lived in?                                                                   | No               |
#      | Do you wish to transfer any unused residence nil rate band?                                                                                              | No               |
#      | Are you claiming any downsizing allowance?                                                                                                               | Yes              |
#      | What was the date the property was disposed of?                                                                                                          | 1 October 2018   |
#      | What was the value of the deceased’s interest in the property at the date it was disposed of?                                                            | £285,000         |
#      | Other than the property that you are claiming residence nil rate band allowance on, do any other assets pass to a direct descendant following the death? | Yes              |
#      | Does grossing-up or interaction apply?                                                                                                                   | No               |
#      | What is the total value of the other assets passing to direct descendants?                                                                               | £250,000         |
#
#  Scenario: 14 - Journey answering Yes to Value Being Transferred, Downsizing, Assets Passing to Direct Descendant and Value Available When Property Changed and no to everything else
#    When I enter 01/09/2020 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £500,000 as the Value of Estate
#    And £400,000 as the Chargeable Estate Value
#    And answer No to the Property In Estate question
#    And click the Continue link on the No Additional Threshold Available page
#    And answer Yes to the Transfer Any Unused Threshold question
#    And enter £175,000 as the Value Being Transferred
#    And answer Yes to the Claim Downsizing Threshold question
#    And enter 01/10/2018 as the Date Property Was Changed
#    And enter £285,000 as the Value Of Changed Property
#    And answer Yes to the Assets Passing To Direct Descendants question
#    And answer No to the Grossing Up On Estate Assets question
#    And enter £250,000 as the Value Of Assets Passing
#    And answer Yes to the Transfer Available When Property Changed question
#    And enter £100,000 as the Value Available When Property Changed
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £250,000
#    When I click the Show previous answers reveal
#    And the following answers
#      | Label                                                                                                                                                    | Value            |
#      | What was the date of death?                                                                                                                              | 1 September 2020 |
#      | Does any of the estate pass to the deceased’s children or other direct descendants?                                                                      | Yes              |
#      | What is the total value of the estate before deducting any reliefs or exemptions?                                                                        | £500,000         |
#      | What is the amount of the total chargeable estate on death?                                                                                              | £400,000         |
#      | Does the estate include any residential property that the deceased owned and lived in?                                                                   | No               |
#      | Do you wish to transfer any unused residence nil rate band?                                                                                              | Yes              |
#      | How much transferable residence nil rate band are you claiming?                                                                                          | £175,000         |
#      | Are you claiming any downsizing allowance?                                                                                                               | Yes              |
#      | What was the date the property was disposed of?                                                                                                          | 1 October 2018   |
#      | What was the value of the deceased’s interest in the property at the date it was disposed of?                                                            | £285,000         |
#      | Other than the property that you are claiming residence nil rate band allowance on, do any other assets pass to a direct descendant following the death? | Yes              |
#      | Does grossing-up or interaction apply?                                                                                                                   | No               |
#      | What is the total value of the other assets passing to direct descendants?                                                                               | £250,000         |
#      | If a valid claim for RNRB had been made at the date of disposal would any transferable residence nil rate band have been available at that time?         | Yes              |
#      | What would the amount of transferable residence nil rate band have been at the date of disposal?                                                         | £100,000         |
