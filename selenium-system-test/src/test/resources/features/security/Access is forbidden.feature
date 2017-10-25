Feature: User CANNOT proceed without going through security

  @HTS-23 @ignore
  Scenario: User CANNOT view the user details page without logging in to Government Gateway
    Given I have NOT logged in to Government Gateway
    When I try to view the user details page
    Then I am prompted to log in to Government Gateway

  @HTS-23
  Scenario: User CANNOT view the create-an-account page without logging in to Government Gateway
    Given I have NOT logged in to Government Gateway
    When I try to view the create-an-account page
    Then I am prompted to log in to Government Gateway

  @HTS-25 @ignore
  Scenario: User CANNOT view user details if they have NOT passed IV
    Given I have logged in to Government Gateway with a confidence level of 100
    When I try to view the user details page
    Then I am forced into going through IV before being able to proceed with their HtS application

  @HTS-25
  Scenario: User CANNOT proceed with application if they have NOT passed IV
    Given I have logged in to Government Gateway with a confidence level of 100
    When I try to view the create-an-account page
    Then I am forced into going through IV before being able to proceed with their HtS application

  Scenario: Ineligible user cannot view the create-an-account page
    Given I have gone through GG/2SV/identity check but I am NOT eligible for Help to Save
    When I try to view the create-an-account page
    Then I still see confirmation that I am NOT eligible
