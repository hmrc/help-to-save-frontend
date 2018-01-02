@HTS-37
Feature: Applicant creates new account

  Scenario: An unauthenticated user creates new account
    Given a user has logged in and passed IV
    When they confirm their details and continue to create an account
    And they select their GG email and proceed
    And they see the final Create Account page
    When they click on accept and create an account

  @zap
  Scenario: An unauthenticated eligible user wishes to create an account but comes through the sign in link
    Given a user tries to sign in through the Apply page
    And they have logged in and passed IV
    Then they will be on a page which says you do not have an account
    When the user clicks on the check eligibility button
    When they confirm their details and continue to create an account
    And they select their GG email and proceed
    Then they see the final Create Account page
    When they click on accept and create an account

  Scenario: An authenticated eligible user wishes to create an account but comes through the sign in link
    Given an authenticated user tries to sign in through the Apply page
    Then they will be on a page which says you do not have an account
    When the user clicks on the check eligibility button
    When they confirm their details and continue to create an account
    And they select their GG email and proceed
    Then they see the final Create Account page
    When they click on accept and create an account