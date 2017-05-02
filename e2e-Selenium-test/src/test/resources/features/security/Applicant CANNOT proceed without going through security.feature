@BrowserStack @vat

Feature: Applicant CANNOT proceed without going through security
  Scenario: Applicant CANNOT proceed with application if they have NOT passed 2SV
    Given an applicant has a confidence level of 200
    But their credential strength is weak
    Then they are forced into undergoing 2SV and CANNOT proceed with their HtS application



