Feature: User CANNOT proceed without going through security

  @HTS-23
  Scenario: User CANNOT view the user details page without logging in to Government Gateway
    Given a user has NOT logged in to Government Gateway
    When they try to view the user details page
    Then they are prompted to log in to Government Gateway

  @HTS-23
  Scenario: User CANNOT view the create-an-account page without logging in to Government Gateway
    Given a user has NOT logged in to Government Gateway
    When they try to view the create-an-account page
    Then they are prompted to log in to Government Gateway

  @HTS-25
  Scenario: User CANNOT view user details if they have NOT passed IV
    Given a user has logged in to Government Gateway with a confidence level of 100
    When they try to view the user details page
    Then they are forced into going through IV before being able to proceed with their HtS application

  @HTS-25
  Scenario: User CANNOT proceed with application if they have NOT passed IV
    Given a user has logged in to Government Gateway with a confidence level of 100
    When they try to view the create-an-account page
    Then they are forced into going through IV before being able to proceed with their HtS application