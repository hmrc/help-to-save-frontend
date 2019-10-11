@iv-journey @zap
Feature: Applicant goes through Identity Verification (IV) journey

  Background:
    Given an applicant who hasn't been through IV applies

  @VerifyHeaderAndFooter @HTS-1183
  Scenario: An eligible applicant passes IV and eligibility check
    When they successfully go through the IV journey
    Then they see that they have passed the eligibility check

  Scenario Outline: An eligible applicant does not pass IV and has to try again
    When they go through the IV journey and fail because of <reason>
    Then they will see the <reason>

    Examples:
      | reason                |
      | Failed IV             |
      | Locked Out            |
      | Insufficient Evidence |
      | Precondition Failed   |
      | Technical Issue       |
      | User Aborted          |
      | Timed Out             |

  Scenario: An eligible applicant does not pass IV with "Failed Matching"
    When they go through the IV journey and fail because of Failed Matching
    Then they will see generic HTS contact page
