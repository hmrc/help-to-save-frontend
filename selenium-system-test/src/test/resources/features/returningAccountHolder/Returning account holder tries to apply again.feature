Feature: Returning account holder tries to apply again

  @HTS-147 @zap @check
  Scenario: An unauthenticated account holder attempts to access their account through the sign in link
    Given a user has previously created an account
    And they try to sign in through the Apply page
    When they log in again
    Then they will be on the account home page