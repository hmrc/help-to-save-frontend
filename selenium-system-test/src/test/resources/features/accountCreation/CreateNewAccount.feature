@ignore
Feature: Create new account

  Scenario: User wishes to apply for a Help to Save account
    Given A user is at the start of the registration process
    When they proceed through to the apply page
    And they click on the Start now button
    Then the GG sign in page is visible

  Scenario: User creates new account
    Given a user has logged in and passed IV
    When they start to create an account
    And they choose to create an account
    Then they see that the account is created

  Scenario: User declines to create new account
    Given a user has logged in and passed IV
    When they start to create an account
    And they choose to not create an account
    Then they see the gov uk page

  Scenario: An unauthenticated eligible user wishes to create an account but comes through the sign in link
    Given a user is on the apply page
    When they click on the sign in link
    And they have logged in and passed IV
    Then they will be on eligibility question page
    When the user clicks on the check eligibility button
    Then they will be on the you're eligible page
    When they start to create an account
    And they choose to create an account
    Then they see that the account is created