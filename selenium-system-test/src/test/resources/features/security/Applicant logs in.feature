Feature: Logging in

  @HTS-23
  Scenario: An unauthenticated user wishes to apply for a Help to Save account
    Given A user is at the start of the registration process
    When they proceed through to the apply page
    And they click on the Start now button
    Then the GG sign in page is visible