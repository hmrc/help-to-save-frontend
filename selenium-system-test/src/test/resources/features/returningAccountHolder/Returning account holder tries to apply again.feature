Feature: Returning account holder tries to apply again

  @HTS-147 @zap
  Scenario: An unauthenticated account holder attempts to access their account through the sign in link
    Given a user has previously created an account
    And the user tries to sign in through the Apply page
    When they have logged in again and passed IV
    Then they will be on the account home page