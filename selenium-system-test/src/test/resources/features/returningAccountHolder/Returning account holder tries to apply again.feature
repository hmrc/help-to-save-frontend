@HTS-147
Feature: Returning account holder tries to apply again

  @zap @done @but-will-need-updating
  Scenario: An unauthenticated user attempts to access their account through the sign in link
    Given a user has previously created an account
    And a user is on the apply page
    When they click on the sign in link
    And they have logged in again and passed IV
    Then they will be on the account home page