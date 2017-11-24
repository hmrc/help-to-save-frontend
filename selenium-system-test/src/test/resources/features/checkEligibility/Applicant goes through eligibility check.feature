@HTS-90
Feature: Applicant goes through eligibility check

@zap @done
  Scenario: User is in receipt of working tax credit and so is eligible
    Given an user is in receipt of working tax credit
    When they apply for Help to Save
    Then they see that they are eligible for Help to Save

@zap @still-to-do @all-ninos-are-eligble-atm
  Scenario: User is NOT in receipt of working tax credit and so is NOT eligible
    Given an user is NOT in receipt of working tax credit
    When they apply for Help to Save
    Then they see that they are NOT eligible for Help to Save
