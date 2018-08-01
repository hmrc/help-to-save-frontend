@HTS-1183
Feature: Make sure all pages use smart quotes only

  Scenario: Check the link expired page uses smart quotes only
    Given they log in
    When a user views the link expired page
    Then they see that the link expired page has only smart quotes

  Scenario: Check the no account page uses smart quotes only
    Given they log in
    When a user views the no account page
    Then they see that the no account page has only smart quotes

  Scenario: Check the create account error page uses smart quotes only
    Given the user has logged in and passed IV
    When they proceed to create an account
    When a user views the create account error page
    Then they see that the create account error page has only smart quotes