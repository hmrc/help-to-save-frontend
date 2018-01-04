@HTS-90 @zap
Feature: Applicant goes through eligibility check

  Scenario: User is in receipt of working tax credit and so is eligible
    Given a user is in receipt of working tax credit
    When they apply for Help to Save
    Then they see that they are eligible for Help to Save

  Scenario: User is NOT in receipt of working tax credit and so is NOT eligible
    Given a user is NOT in receipt of working tax credit
    When they apply for Help to Save
    Then they see that they are NOT eligible for Help to Save