@ignore
Feature: Applicant creates new account

  Scenario: Applicant creates new account
    Given an applicant has logged in and passed IV
    When they choose to create an account
    Then they see that the account is created
