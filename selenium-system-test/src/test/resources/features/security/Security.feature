@RunOnlyInDev
Feature: User CANNOT proceed without going through security

  Scenario: User CANNOT view the user details page without logging in
    Given a user has NOT logged in
    When they try to view the user details page
    Then they are prompted to log in

  Scenario: User CANNOT view the create-an-account page without logging in
    Given a user has NOT logged in
    When they try to view the create-an-account page
    Then they are prompted to log in

  Scenario: User CANNOT proceed with application if they have NOT passed IV
    Given a user has logged in
    But their confidence level is 100
    When they try to view the user details page
    Then they are forced into going through IV before being able to proceed with their HtS application

  Scenario: User CANNOT proceed with application if they have NOT passed IV
    Given a user has logged in
    But their confidence level is 100
    When they try to view the create-an-account page
    Then they are forced into going through IV before being able to proceed with their HtS application