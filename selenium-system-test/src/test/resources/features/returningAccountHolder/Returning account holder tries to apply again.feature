@ignore
  #Temporarily set to @ignore as even though it passes locally, it seem to doesn't work on ci-dev
Feature: Returning account holder tries to apply again

  @HTS-147 @zap
  Scenario: An unauthenticated account holder attempts to access their account through the sign in link
    Given a user has previously created an account
    And they try to sign in through the Apply page without being logged in GG
    When they log in again
    Then they will be on the account home page