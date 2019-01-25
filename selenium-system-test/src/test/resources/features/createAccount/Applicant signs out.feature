@JONS-TEST
Feature: Applicant signs out

  Scenario: Applicant signs in then proceeds to sign out
    Given they try to start creating an account
    And they are prompted to log into GG
    When they log in and proceed to create an account using their GG email
    And they are redirected to the account created page
    When they click on the sign out link
    Then they are redirected to the survey page