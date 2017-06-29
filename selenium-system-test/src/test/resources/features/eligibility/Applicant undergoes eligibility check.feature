@ignore
Feature: Applicant undergoes eligibility check

  Scenario: Applicant is in receipt of working tax credit and so is eligible
    Given an applicant is in receipt of working tax credit
    When they apply for Help to Save
    Then they see that they are eligible for Help to Save

  Scenario: Applicant is NOT in receipt of working tax credit and so is NOT eligible
    Given an applicant is NOT in receipt of working tax credit
    When they apply for Help to Save
    Then they see that they are NOT eligible for Help to Save

