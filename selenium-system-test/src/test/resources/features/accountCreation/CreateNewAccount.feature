@ignore
Feature: Create new account

  Scenario: User wishes to apply for a Help to Save account
    Given A user is at the start of the registration process
    When they proceed through to the apply page
    And they click on the Start now button
    Then the GG sign in page is visible

    #Currently not working in DEV
#  Scenario: User creates new account
#    Given an applicant has logged in and passed IV
#    When They start to create an account
#    And they choose to create an account
#    Then they see that the account is created

  Scenario: User declines to create new account
    Given an applicant has logged in and passed IV
    When They start to create an account
    And they choose to not create an account
    Then they see the gov uk page