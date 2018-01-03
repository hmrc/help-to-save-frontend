@HTS-37 @creating-account
Feature: Applicant creates new account

  Scenario: An unauthenticated user creates new account
    Given the user has logged in and passed IV
    And they confirm their details and continue to create an account
    And they select their GG email and proceed
    When they see the final Create Account page
    Then they click on accept and create an account

  @zap
  Scenario: An unauthenticated eligible user wishes to create an account but comes through the sign in link
    Given the user has logged in and passed IV
    And the user tries to sign in through the Apply page
    And they see a page stating they don't have an account
    And the user continues
    And they confirm their details and continue to create an account
    And they select their GG email and proceed
    When they see the final Create Account page
    Then they click on accept and create an account

  Scenario: An authenticated eligible user wishes to create an account but comes through the sign in link
    Given an authenticated user tries to sign in through the Apply page
    And they see a page stating they don't have an account
    And the user continues
    And they confirm their details and continue to create an account
    And they select their GG email and proceed
    When they see the final Create Account page
    Then they click on accept and create an account