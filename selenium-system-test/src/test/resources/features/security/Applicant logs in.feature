Feature: Logging in

  @HTS-23
  Scenario: An unauthenticated user wishes to apply for a Help to Save account
    Given A user is at the start of the registration process
    When they proceed through to the apply page
    And they click on the Start now button
    Then the GG sign in page is visible

  @WIP
  Scenario: An eligible applicant passes identity verification and passes eligibility check
    Given an applicant who hasn't been through identity verification is on the Apply page
    When they go through identity verification check successfully and continue
    Then they will be redirected to the eligibility check and pass it