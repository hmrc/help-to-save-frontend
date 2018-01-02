Feature: Returning account holder tries to apply again

  @HTS-147 @zap
  Scenario: An unauthenticated account holder attempts to access their account through the sign in link
    Given a user tries to sign in through the Apply page
    And they have logged in again and passed IV
    Then they will be on the account home page

  @HTS-420
  Scenario: A user without an account attempts to go to the account home page
    Given a user tries to sign in through the Apply page
    And they have logged in again and passed IV
    Then they will be on a page which says you do not have an account