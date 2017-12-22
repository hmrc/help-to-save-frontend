@HTS-37 @creating-account
Feature: Applicant creates new account

  Scenario: An unauthenticated user creates new account
    Given they try to start creating an account from the Apply page
    And they are prompted to log into GG
    When they log in and proceed to create an account using their GG email
    Then they see that the account is created

  @zap @HTS-23 @BrowserStack
  Scenario: An unauthenticated eligible user wishes to create an account but comes through the sign in link
    Given they try to sign in through the Apply page without being logged in GG
    And they are prompted to log into GG
    When they log in
    And they are informed they don't have an account
    And they proceed to create an account using their GG email
    Then they see that the account is created

  @HTS-420
  Scenario: An authenticated eligible user wishes to create an account but comes through the sign in link
    Given the authenticated user tries to sign in through the Apply page
    And they are informed they don't have an account
    When they proceed to create an account using their GG email
    Then they see that the account is created

  Scenario: An authenticated eligible user wishes to create account but sees their details are incorrect
    Given they apply for Help to Save
    When they see their details are incorrect and report it
    Then they see the relevant page
