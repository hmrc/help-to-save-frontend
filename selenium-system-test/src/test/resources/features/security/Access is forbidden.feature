@security
Feature: Applicant CANNOT proceed without going through security

#  @HTS-23
#  Scenario: Applicant CANNOT view the user details page without logging in to Government Gateway
#    When they try to view their details without having logged in to GG
#    Then they are prompted to log into GG
#
#  @HTS-23
#  Scenario: Applicant CANNOT view the create-an-account page without logging in to Government Gateway
#    When they try to view the create-an-account page
#    Then they are prompted to log into GG
#
#  Scenario: Ineligible applicant cannot view the create-an-account page
#    Given they have gone through GG/2SV/identity check but they are NOT eligible for Help to Save
#    When they try to view the create-an-account page
#    Then they still see confirmation that they are NOT eligible
#
#  @HTS-25
#  Scenario Outline: Applicant CANNOT proceed with application if they have NOT passed IV
#    Given they have logged into Government Gateway with a confidence level of <level>
#    When they try to view the create-an-account page
#    Then they are forced into going through IV before being able to proceed with their HtS application
#
#  Examples:
#      | level |
#      | 50    |
#      | 100   |
#
#  @HTS-25
#  Scenario Outline: Applicant CANNOT view user details if they have NOT passed IV
#    Given they have logged into Government Gateway with a confidence level of <level>
#    When they try to view their details without having logged in to GG
#    Then they are forced into going through IV before being able to proceed with their HtS application
#
#    Examples:
#      | level |
#      | 50    |
#      | 100   |
