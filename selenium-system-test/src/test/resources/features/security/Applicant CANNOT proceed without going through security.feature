Feature: Applicant CANNOT proceed without going through security

  @ignore
  Scenario: Applicant CANNOT view the user details page without logging in
    Given an applicant has NOT logged in
    When they try to view the user details page
    Then they are prompted to log in

  Scenario: Applicant CANNOT view the create-an-account page without logging in
    Given an applicant has NOT logged in
    When they try to view the create-an-account page
    Then they are prompted to log in

  @ignore
  Scenario: Applicant CANNOT proceed with application if they have NOT passed IV
    Given an applicant has logged in
    But their confidence level is 100
    When they try to view the user details page
    Then they are forced into going through IV before being able to proceed with their HtS application

  @ignore
  Scenario: Applicant CANNOT proceed with application if they have NOT passed IV
    Given an applicant has logged in
    But their confidence level is 100
    When they try to view the create-an-account page
    Then they are forced into going through IV before being able to proceed with their HtS application