Feature: Applicant changes their email

  @HTS-33 @HTS-398
  Scenario: An applicant changes their email
    Given I am viewing my applicant details
    When I choose to change my email address
    Then I am asked to check my email account for a verification email

  @HTS-400
  Scenario: Applicant requests a re-send of the verification email
    Given I've chosen to change my email address from A to B during the application process
    But I want to receive a second verification email
    When I request a re-send of the verification email
    Then I am asked to check my email account for a verification email

  @HTS-399
  Scenario: Applicant changes their mind about the new address they provided
    Given I've chosen to change my email address from A to B during the application process
    But I haven't yet verified new email address B
    When I then choose to change the email address from B to C
    Then I am asked to check my email account for a verification email