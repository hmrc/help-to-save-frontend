@HTS-37
Feature: Applicant creates new account

  @QA
  Scenario: An unauthenticated user creates new account
    Given a user has logged in and passed IV
    When they start to create an account
    And they choose to create an account
    Then they see that the account is created

  @zap
  Scenario: An unauthenticated eligible user wishes to create an account but comes through the sign in link
    Given a user is on the apply page
    When they click on the sign in link
    And they have logged in and passed IV
    Then they will be on a page which says you do not have an account
    When the user clicks on the check eligibility button
    Then they will be on the you're eligible page
    When they start to create an account
    And they choose to create an account
    Then they see that the account is created

  Scenario: An authenticated eligible user wishes to create an account but comes through the sign in link
    Given an authenticated user is on the apply page
    When they click on the sign in link
    Then they will be on a page which says you do not have an account
    When the user clicks on the check eligibility button
    Then they will be on the you're eligible page
    When they start to create an account
    And they choose to create an account
    Then they see that the account is created