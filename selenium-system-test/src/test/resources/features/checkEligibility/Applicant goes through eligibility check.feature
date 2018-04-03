@HTS-90
Feature: Applicant goes through eligibility check

  Scenario: User is in receipt of working tax credit and so is eligible
    Given a user is in receipt of working tax credit
    When they apply for Help to Save
    Then they see that they are eligible for Help to Save

  @BrowserStack
  Scenario: User is NOT in receipt of working tax credit and so is NOT eligible
    Given a user is NOT in receipt of working tax credit
    When they apply for Help to Save
    Then they see that they are NOT eligible for Help to Save

  @HTS-859
  Scenario: User experiences a technical error
    Given DES is down
    When they apply for Help to Save
    Then they see a technical error page

  @HTS-906
  Scenario: User is NOT eligible and wishes to view appeals page
    Given a user is NOT in receipt of working tax credit
    When they apply for Help to Save when NOT eligible
    Then they see that they are NOT eligible for Help to Save
    When they then click on still think you're eligible link
    Then they see appeals and tax tribunal page