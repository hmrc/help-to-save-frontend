Feature: Applicant CANNOT proceed without going through security

  Scenario: Applicant CANNOT proceed with application if they have NOT passed 2SV
    Given an applicant has a confidence level of 200
    But their credential strength is weak
    Then they are forced into going through 2SV before being able to proceed with their HtS application

  Scenario: Applicant CANNOT proceed with application if they have NOT passed IV
    Given an applicant's credential strength is strong
    But their confidence level is 100
    Then they are forced into going through IV before being able to proceed with their HtS application



