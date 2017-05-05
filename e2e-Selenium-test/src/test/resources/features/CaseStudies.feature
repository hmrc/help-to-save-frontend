#@Suite
#Feature: Case Studies
#
#  Background:
#    Given I am on the Date of Death page
#
#  Scenario: Case Study 1
#    When I enter 01/01/2021 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £490,000 as the Value of Estate
#    And £490,000 as the Chargeable Estate Value
#    And answer Yes to the Property In Estate question
#    And enter £300,000 as the property value
#    And answer Yes, all of it passed to Property Passing To Direct Descendants
#    And answer No to the Exemptions And Relief Claimed question
#    And answer No to the Transfer Any Unused Threshold question
#    And answer No to the Claim Downsizing Threshold question
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £175,000
#
#  Scenario: Case Study 2
#    When I enter 01/01/2021 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £1,000,000 as the Value of Estate
#    And £500,000 as the Chargeable Estate Value
#    And answer Yes to the Property In Estate question
#    And enter £100,000 as the property value
#    And answer Yes, all of it passed to Property Passing To Direct Descendants
#    And answer No to the Exemptions And Relief Claimed question
#    And answer No to the Transfer Any Unused Threshold question
#    And answer No to the Claim Downsizing Threshold question
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £100,000

#  Scenario: Case Study 3
#    When I enter 01/01/2021 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £450,000 as the Value of Estate
#    And £450,000 as the Chargeable Estate Value
#    And answer Yes to the Property In Estate question
#    And enter £200,000 as the property value
#    And answer Yes, all of it passed to Property Passing To Direct Descendants
#    And answer No to the Exemptions And Relief Claimed question
#    And answer No to the Transfer Any Unused Threshold question
#    And answer No to the Claim Downsizing Threshold question
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £175,000
#
#  Scenario: Case Study 4
#    When I enter 01/01/2021 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £750,000 as the Value of Estate
#    And £750,000 as the Chargeable Estate Value
#    And answer Yes to the Property In Estate question
#    And enter £500,000 as the property value
#    And answer Yes, all of it passed to Property Passing To Direct Descendants
#    And answer No to the Exemptions And Relief Claimed question
#    And answer No to the Transfer Any Unused Threshold question
#    And answer No to the Claim Downsizing Threshold question
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £175,000
#
#  Scenario: Case Study 5
#    When I enter 30/07/2019 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £1,000,000 as the Value of Estate
#    And £1,000,000 as the Chargeable Estate Value
#    And answer Yes to the Property In Estate question
#    And enter £400,000 as the property value
#    And answer Yes, all of it passed to Property Passing To Direct Descendants
#    And answer No to the Exemptions And Relief Claimed question
#    And answer Yes to the Transfer Any Unused Threshold question
#    And enter £150,000 as the Value Being Transferred
#    And answer No to the Claim Downsizing Threshold question
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £300,000
#
#  Scenario: Case Study 6
#    When I enter 01/01/2021 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £800,000 as the Value of Estate
#    And £800,000 as the Chargeable Estate Value
#    And answer Yes to the Property In Estate question
#    And enter £500,000 as the property value
#    And answer Yes, some of it passed to Property Passing To Direct Descendants
#    And enter 50% as the Percentage Passed To Direct Descendants
#    And answer No to the Exemptions And Relief Claimed question
#    And answer No to the Transfer Any Unused Threshold question
#    And answer No to the Claim Downsizing Threshold question
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £175,000
#
#  Scenario: Case Study 7
#    When I enter 01/12/2019 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £900,000 as the Value of Estate
#    And £900,000 as the Chargeable Estate Value
#    And answer Yes to the Property In Estate question
#    And enter £500,000 as the property value
#    And answer Yes, all of it passed to Property Passing To Direct Descendants
#    And answer No to the Exemptions And Relief Claimed question
#    And answer No to the Transfer Any Unused Threshold question
#    And answer No to the Claim Downsizing Threshold question
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £150,000
#
#  Scenario: Case Study 8
#    When I enter 01/12/2020 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £1,250,000 as the Value of Estate
#    And £1,200,000 as the Chargeable Estate Value
#    And answer Yes to the Property In Estate question
#    And enter £400,000 as the property value
#    And answer Yes, all of it passed to Property Passing To Direct Descendants
#    And answer No to the Exemptions And Relief Claimed question
#    And answer Yes to the Transfer Any Unused Threshold question
#    And enter £175,000 as the Value Being Transferred
#    And answer No to the Claim Downsizing Threshold question
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £350,000
#
#  Scenario: Case Study 9
#    When I enter 01/12/2018 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £2,100,000 as the Value of Estate
#    And £2,100,000 as the Chargeable Estate Value
#    And answer Yes to the Property In Estate question
#    And enter £450,000 as the property value
#    And answer Yes, all of it passed to Property Passing To Direct Descendants
#    And answer No to the Exemptions And Relief Claimed question
#    And answer No to the Transfer Any Unused Threshold question
#    And answer No to the Claim Downsizing Threshold question
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £75,000
#
#  Scenario: Case Study 10
#    When I enter 01/05/2018 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £2,100,000 as the Value of Estate
#    And £2,100,000 as the Chargeable Estate Value
#    And answer Yes to the Property In Estate question
#    And enter £450,000 as the property value
#    And answer No to Property Passing To Direct Descendants
#    And click the Continue link on the No Additional Threshold Available page
#    And answer No to the Transfer Any Unused Threshold question
#    And answer No to the Claim Downsizing Threshold question
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £0
#
#  Scenario: Case Study 10A
#    When I enter 01/12/2020 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £1,800,000 as the Value of Estate
#    And £1,800,000 as the Chargeable Estate Value
#    And answer Yes to the Property In Estate question
#    And enter £500,000 as the property value
#    And answer Yes, all of it passed to Property Passing To Direct Descendants
#    And answer No to the Exemptions And Relief Claimed question
#    And answer Yes to the Transfer Any Unused Threshold question
#    And enter £105,000 as the Value Being Transferred
#    And answer No to the Claim Downsizing Threshold question
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £280,000
#
#  Scenario: Case Study 10 alternative 1
#    When I enter 01/12/2020 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £2,100,000 as the Value of Estate
#    And £2,100,000 as the Chargeable Estate Value
#    And answer Yes to the Property In Estate question
#    And enter £500,000 as the property value
#    And answer Yes, all of it passed to Property Passing To Direct Descendants
#    And answer No to the Exemptions And Relief Claimed question
#    And answer Yes to the Transfer Any Unused Threshold question
#    And enter £105,000 as the Value Being Transferred
#    And answer No to the Claim Downsizing Threshold question
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £230,000
#
#  Scenario: Case Study 10 alternative 2
#    When I enter 10/10/2018 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £2,100,000 as the Value of Estate
#    And £2,100,000 as the Chargeable Estate Value
#    And answer Yes to the Property In Estate question
#    And enter £500,000 as the property value
#    And answer No to Property Passing To Direct Descendants
#    And click the Continue link on the No Additional Threshold Available page
#    And answer No to the Transfer Any Unused Threshold question
#    And answer No to the Claim Downsizing Threshold question
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £0
#
#  Scenario: Case Study 10 alternative 3
#    When I enter 10/10/2020 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £2,100,000 as the Value of Estate
#    And £2,100,000 as the Chargeable Estate Value
#    And answer Yes to the Property In Estate question
#    And enter £500,000 as the property value
#    And answer Yes, all of it passed to Property Passing To Direct Descendants
#    And answer No to the Exemptions And Relief Claimed question
#    And answer Yes to the Transfer Any Unused Threshold question
#    And enter £105,000 as the Value Being Transferred
#    And answer No to the Claim Downsizing Threshold question
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £230,000
#
#  Scenario: Case Study 11
#    When I enter 01/08/2020 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £850,000 as the Value of Estate
#    And £850,000 as the Chargeable Estate Value
#    And answer No to the Property In Estate question
#    And click the Continue link on the No Additional Threshold Available page
#    And answer Yes to the Transfer Any Unused Threshold question
#    And enter £175,000 as the Value Being Transferred
#    And answer Yes to the Claim Downsizing Threshold question
#    And enter 01/06/2018 as the Date Property Was Changed
#    And enter £195,000 as the Value Of Changed Property
#    And answer Yes to the Assets Passing To Direct Descendants question
#    And answer No to the Grossing Up On Estate Assets question
#    And enter £850,000 as the Value Of Assets Passing
#    And answer Yes to the Transfer Available When Property Changed question
#    And enter £125,000 as the Value Available When Property Changed
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £227,500
#
#  Scenario: Case Study 12
#    When I enter 01/08/2020 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £700,000 as the Value of Estate
#    And £700,000 as the Chargeable Estate Value
#    And answer Yes to the Property In Estate question
#    And enter £200,000 as the property value
#    And answer No to Property Passing To Direct Descendants
#    And click the Continue link on the No Additional Threshold Available page
#    And answer No to the Transfer Any Unused Threshold question
#    And answer Yes to the Claim Downsizing Threshold question
#    And enter 01/10/2018 as the Date Property Was Changed
#    And enter £450,000 as the Value Of Changed Property
#    And answer Yes to the Assets Passing To Direct Descendants question
#    And answer No to the Grossing Up On Estate Assets question
#    And enter £500,000 as the Value Of Assets Passing
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £0
#
#  Scenario: Case Study 13
#    When I enter 01/09/2020 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £305,000 as the Value of Estate
#    And £305,000 as the Chargeable Estate Value
#    And answer Yes to the Property In Estate question
#    And enter £105,000 as the property value
#    And answer Yes, all of it passed to Property Passing To Direct Descendants
#    And answer No to the Exemptions And Relief Claimed question
#    And answer No to the Transfer Any Unused Threshold question
#    And answer Yes to the Claim Downsizing Threshold question
#    And enter 01/05/2018 as the Date Property Was Changed
#    And enter £500,000 as the Value Of Changed Property
#    And answer Yes to the Assets Passing To Direct Descendants question
#    And answer No to the Grossing Up On Estate Assets question
#    And enter £200,000 as the Value Of Assets Passing
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £175,000
#
#  Scenario: Case Study 13A
#    When I enter 01/09/2020 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £305,000 as the Value of Estate
#    And £305,000 as the Chargeable Estate Value
#    And answer Yes to the Property In Estate question
#    And enter £105,000 as the property value
#    And answer Yes, all of it passed to Property Passing To Direct Descendants
#    And answer No to the Exemptions And Relief Claimed question
#    And answer No to the Transfer Any Unused Threshold question
#    And answer Yes to the Claim Downsizing Threshold question
#    And enter 01/05/2018 as the Date Property Was Changed
#    And enter £500,000 as the Value Of Changed Property
#    And answer Yes to the Assets Passing To Direct Descendants question
#    And answer No to the Grossing Up On Estate Assets question
#    And enter £50,000 as the Value Of Assets Passing
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £155,000
#
#  Scenario: Case Study 14
#    When I enter 01/09/2020 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £700,000 as the Value of Estate
#    And £700,000 as the Chargeable Estate Value
#    And answer Yes to the Property In Estate question
#    And enter £105,000 as the property value
#    And answer Yes, some of it passed to Property Passing To Direct Descendants
#    And enter 50% as the Percentage Passed To Direct Descendants
#    And answer Yes to the Exemptions And Relief Claimed question
#    And answer No to the Grossing Up On Estate Property question
#    And enter £52,500 as the Chargeable Property Value
#    And enter £52,500 as the Chargeable Inherited Property Value
#    And answer No to the Transfer Any Unused Threshold question
#    And answer Yes to the Claim Downsizing Threshold question
#    And enter 01/02/2019 as the Date Property Was Changed
#    And enter £400,000 as the Value Of Changed Property
#    And answer Yes to the Assets Passing To Direct Descendants question
#    And answer No to the Grossing Up On Estate Assets question
#    And enter £150,000 as the Value Of Assets Passing
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £122,500
#
#  Scenario: Case Study 14A
#    When I enter 01/09/2020 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £700,000 as the Value of Estate
#    And £700,000 as the Chargeable Estate Value
#    And answer Yes to the Property In Estate question
#    And enter £105,000 as the property value
#    And answer Yes, some of it passed to Property Passing To Direct Descendants
#    And enter 50% as the Percentage Passed To Direct Descendants
#    And answer Yes to the Exemptions And Relief Claimed question
#    And answer No to the Grossing Up On Estate Property question
#    And enter £52,500 as the Chargeable Property Value
#    And enter £52,500 as the Chargeable Inherited Property Value
#    And answer No to the Transfer Any Unused Threshold question
#    And answer Yes to the Claim Downsizing Threshold question
#    And enter 01/02/2019 as the Date Property Was Changed
#    And enter £400,000 as the Value Of Changed Property
#    And answer Yes to the Assets Passing To Direct Descendants question
#    And answer No to the Grossing Up On Estate Assets question
#    And enter £20,000 as the Value Of Assets Passing
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £72,500
#
#  Scenario: Case Study 15
#    When I enter 01/12/2019 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £185,000 as the Value of Estate
#    And £80,000 as the Chargeable Estate Value
#    And answer Yes to the Property In Estate question
#    And enter £105,000 as the property value
#    And answer No to Property Passing To Direct Descendants
#    And click the Continue link on the No Additional Threshold Available page
#    And answer No to the Transfer Any Unused Threshold question
#    And answer Yes to the Claim Downsizing Threshold question
#    And enter 01/03/2016 as the Date Property Was Changed
#    And enter £150,000 as the Value Of Changed Property
#    And answer Yes to the Assets Passing To Direct Descendants question
#    And answer No to the Grossing Up On Estate Assets question
#    And enter £80,000 as the Value Of Assets Passing
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £45,000
#
#  Scenario: Case Study 15A
#    When I enter 01/12/2019 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £185,000 as the Value of Estate
#    And £80,000 as the Chargeable Estate Value
#    And answer Yes to the Property In Estate question
#    And enter £105,000 as the property value
#    And answer No to Property Passing To Direct Descendants
#    And click the Continue link on the No Additional Threshold Available page
#    And answer No to the Transfer Any Unused Threshold question
#    And answer Yes to the Claim Downsizing Threshold question
#    And enter 01/03/2016 as the Date Property Was Changed
#    And enter £150,000 as the Value Of Changed Property
#    And answer Yes to the Assets Passing To Direct Descendants question
#    And answer No to the Grossing Up On Estate Assets question
#    And enter £10,000 as the Value Of Assets Passing
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £10,000
#
#  Scenario: Case Study 15 alternative 1
#    When I enter 01/12/2019 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £185,000 as the Value of Estate
#    And £80,000 as the Chargeable Estate Value
#    And answer Yes to the Property In Estate question
#    And enter £105,000 as the property value
#    And answer No to Property Passing To Direct Descendants
#    And click the Continue link on the No Additional Threshold Available page
#    And answer No to the Transfer Any Unused Threshold question
#    And answer Yes to the Claim Downsizing Threshold question
#    And enter 07/07/2015 as the Date Property Was Changed
#    Then I should be on the No Downsizing Threshold Increase page
#
#  Scenario: Case Study 16
#    When I enter 01/03/2021 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £500,000 as the Value of Estate
#    And £500,000 as the Chargeable Estate Value
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
#
#  Scenario: Case Study 16A
#    When I enter 01/03/2021 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £500,000 as the Value of Estate
#    And £500,000 as the Chargeable Estate Value
#    And answer No to the Property In Estate question
#    And click the Continue link on the No Additional Threshold Available page
#    And answer No to the Transfer Any Unused Threshold question
#    And answer Yes to the Claim Downsizing Threshold question
#    And enter 01/10/2018 as the Date Property Was Changed
#    And enter £285,000 as the Value Of Changed Property
#    And answer Yes to the Assets Passing To Direct Descendants question
#    And answer No to the Grossing Up On Estate Assets question
#    And enter £100,000 as the Value Of Assets Passing
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £100,000
#
#  Scenario: Case Study 17
#    When I enter 01/01/2021 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £600,000 as the Value of Estate
#    And £600,000 as the Chargeable Estate Value
#    And answer No to the Property In Estate question
#    And click the Continue link on the No Additional Threshold Available page
#    And answer No to the Transfer Any Unused Threshold question
#    And answer Yes to the Claim Downsizing Threshold question
#    And enter 01/05/2019 as the Date Property Was Changed
#    And enter £90,000 as the Value Of Changed Property
#    And answer Yes to the Assets Passing To Direct Descendants question
#    And answer No to the Grossing Up On Estate Assets question
#    And enter £600,000 as the Value Of Assets Passing
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £105,000
#
#  Scenario: Case Study 17 alternative 1
#    When I enter 01/01/2021 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £100,000 as the Value of Estate
#    And £100,000 as the Chargeable Estate Value
#    And answer No to the Property In Estate question
#    And click the Continue link on the No Additional Threshold Available page
#    And answer No to the Transfer Any Unused Threshold question
#    And answer Yes to the Claim Downsizing Threshold question
#    And enter 01/05/2019 as the Date Property Was Changed
#    And enter £90,000 as the Value Of Changed Property
#    And answer Yes to the Assets Passing To Direct Descendants question
#    And answer No to the Grossing Up On Estate Assets question
#    And enter £100,000 as the Value Of Assets Passing
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £100,000
#
#  Scenario: Case Study 18
#    When I enter 01/03/2021 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £500,000 as the Value of Estate
#    And £500,000 as the Chargeable Estate Value
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
#    And enter £125,000 as the Value Available When Property Changed
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £250,000
#
#  Scenario: Case Study 19
#    When I enter 01/05/2020 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £635,000 as the Value of Estate
#    And £635,000 as the Chargeable Estate Value
#    And answer Yes to the Property In Estate question
#    And enter £210,000 as the property value
#    And answer Yes, all of it passed to Property Passing To Direct Descendants
#    And answer No to the Exemptions And Relief Claimed question
#    And answer Yes to the Transfer Any Unused Threshold question
#    And enter £175,000 as the Value Being Transferred
#    And answer Yes to the Claim Downsizing Threshold question
#    And enter 01/09/2015 as the Date Property Was Changed
#    And enter £300,000 as the Value Of Changed Property
#    And answer Yes to the Assets Passing To Direct Descendants question
#    And answer No to the Grossing Up On Estate Assets question
#    And enter £425,000 as the Value Of Assets Passing
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £350,000
#
#  Scenario: Case Study 20
#    When I enter 01/12/2019 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £1,900,000 as the Value of Estate
#    And £1,900,000 as the Chargeable Estate Value
#    And answer Yes to the Property In Estate question
#    And enter £240,000 as the property value
#    And answer Yes, all of it passed to Property Passing To Direct Descendants
#    And answer No to the Exemptions And Relief Claimed question
#    And answer Yes to the Transfer Any Unused Threshold question
#    And enter £150,000 as the Value Being Transferred
#    And answer Yes to the Claim Downsizing Threshold question
#    And enter 01/07/2019 as the Date Property Was Changed
#    And enter £285,000 as the Value Of Changed Property
#    And answer Yes to the Assets Passing To Direct Descendants question
#    And answer No to the Grossing Up On Estate Assets question
#    And enter £1,660,000 as the Value Of Assets Passing
#    And answer Yes to the Transfer Available When Property Changed question
#    And enter £150,000 as the Value Available When Property Changed
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £285,000
#
#  Scenario: Case Study 20A
#    When I enter 01/12/2019 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £1,900,000 as the Value of Estate
#    And £1,900,000 as the Chargeable Estate Value
#    And answer Yes to the Property In Estate question
#    And enter £240,000 as the property value
#    And answer Yes, all of it passed to Property Passing To Direct Descendants
#    And answer No to the Exemptions And Relief Claimed question
#    And answer Yes to the Transfer Any Unused Threshold question
#    And enter £150,000 as the Value Being Transferred
#    And answer Yes to the Claim Downsizing Threshold question
#    And enter 01/07/2017 as the Date Property Was Changed
#    And enter £285,000 as the Value Of Changed Property
#    And answer Yes to the Assets Passing To Direct Descendants question
#    And answer No to the Grossing Up On Estate Assets question
#    And enter £1,660,000 as the Value Of Assets Passing
#    And answer Yes to the Transfer Available When Property Changed question
#    And enter £100,000 as the Value Available When Property Changed
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £300,000
#
#  Scenario: Case Study 21
#    When I enter 01/08/2020 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £1,500,000 as the Value of Estate
#    And £1,500,000 as the Chargeable Estate Value
#    And answer Yes to the Property In Estate question
#    And enter £150,000 as the property value
#    And answer Yes, all of it passed to Property Passing To Direct Descendants
#    And answer No to the Exemptions And Relief Claimed question
#    And answer No to the Transfer Any Unused Threshold question
#    And answer Yes to the Claim Downsizing Threshold question
#    And enter 01/05/2020 as the Date Property Was Changed
#    And enter £200,000 as the Value Of Changed Property
#    And answer Yes to the Assets Passing To Direct Descendants question
#    And answer No to the Grossing Up On Estate Assets question
#    And enter £200,000 as the Value Of Assets Passing
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £175,000
#
#  Scenario: Case Study 21A
#    When I enter 01/08/2020 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £1,500,000 as the Value of Estate
#    And £1,500,000 as the Chargeable Estate Value
#    And answer Yes to the Property In Estate question
#    And enter £150,000 as the property value
#    And answer No to Property Passing To Direct Descendants
#    And click the Continue link on the No Additional Threshold Available page
#    And answer No to the Transfer Any Unused Threshold question
#    And answer Yes to the Claim Downsizing Threshold question
#    And enter 01/05/2020 as the Date Property Was Changed
#    And enter £200,000 as the Value Of Changed Property
#    And answer Yes to the Assets Passing To Direct Descendants question
#    And answer No to the Grossing Up On Estate Assets question
#    And enter £200,000 as the Value Of Assets Passing
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £25,000
#
#  Scenario: Case Study 22
#    When I enter 01/08/2020 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £1,500,000 as the Value of Estate
#    And £1,500,000 as the Chargeable Estate Value
#    And answer No to the Property In Estate question
#    And click the Continue link on the No Additional Threshold Available page
#    And answer No to the Transfer Any Unused Threshold question
#    And answer Yes to the Claim Downsizing Threshold question
#    And enter 01/05/2020 as the Date Property Was Changed
#    And enter £200,000 as the Value Of Changed Property
#    And answer Yes to the Assets Passing To Direct Descendants question
#    And answer No to the Grossing Up On Estate Assets question
#    And enter £200,000 as the Value Of Assets Passing
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £175,000
#
#  Scenario: Case Study 23
#    When I enter 01/05/2020 as the date of death
#    And answer Yes to the Part of Estate Passing to Direct Descendants question
#    And £800,000 as the Value of Estate
#    And £800,000 as the Chargeable Estate Value
#    And answer Yes to the Property In Estate question
#    And enter £90,000 as the property value
#    And answer Yes, all of it passed to Property Passing To Direct Descendants
#    And answer No to the Exemptions And Relief Claimed question
#    And answer Yes to the Transfer Any Unused Threshold question
#    And enter £175,000 as the Value Being Transferred
#    And answer Yes to the Claim Downsizing Threshold question
#    And enter 01/10/2018 as the Date Property Was Changed
#    And enter £500,000 as the Value Of Changed Property
#    And answer Yes to the Assets Passing To Direct Descendants question
#    And answer No to the Grossing Up On Estate Assets question
#    And enter £710,000 as the Value Of Assets Passing
#    And answer Yes to the Transfer Available When Property Changed question
#    And enter £125,000 as the Value Available When Property Changed
#    Then I should be on the Threshold Calculation Result page
#    And it should contain an RNRB amount of £350,000
