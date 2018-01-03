Feature: Logging in

  @HTS-23
  Scenario: An unauthenticated user wishes to apply for a Help to Save account
    Given an applicant is on the home page
    When they proceed to the Apply page and click on the Start now button
    Then the GG sign in page is visible

  @HTS-420
  Scenario: A user without an account attempts to go to the account home page
    Given the user tries to sign in through the Apply page
    And they have logged in again and passed IV
    Then they see a page stating they don't have an account