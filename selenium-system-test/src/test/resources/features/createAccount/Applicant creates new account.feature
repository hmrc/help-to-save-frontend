@HTS-37 @creating-account
Feature: Applicant creates new account
  @check-test
  Scenario: An unauthenticated user creates new account
    Given they try to start creating an account
    And they are prompted to log into GG
    When they log in and proceed to create an account using their GG email
    Then they see that the account is created

  @zap @HTS-23 @HTS-1183
  Scenario: An unauthenticated eligible user wishes to create an account but comes through the sign in link
    Given they try to sign in without being logged in to GG
    And they are prompted to log into GG
    When they log in
    And they are informed they don't have an account
    And they proceed to create an account using their GG email
    Then they see that the account is created

  @HTS-420 @VerifyHeaderAndFooter
  Scenario: An authenticated eligible user wishes to create an account but comes through the sign in link
    Given the authenticated user tries to sign in
    And they are informed they don't have an account
    When they proceed to create an account using their GG email
    Then they see that the account is created

  @VerifyHeaderAndFooter @HTS-1183
  Scenario: An authenticated eligible user wishes to create account but sees their details are incorrect
    Given they apply for Help to Save
    When they see their details are incorrect and report it
    Then they see the HMRC change of details page
