@iv-journey @zap
Feature: Applicant goes through Identity Verification (IV) journey

  Scenario: An eligible applicant passes IV and eligibility check
    Given an applicant who hasn't been through IV applies
    When they go through IV check successfully and continue
    Then they will be redirected to the eligibility check and pass it

  Scenario Outline: An eligible applicant does not pass IV and has to try again
    Given an applicant who hasn't been through IV applies
    When they go through IV and fail because of <reason>
    Then they will see the <reason> page

    Examples:
      | reason                |
      | Failed IV             |
      | Precondition Failed   |
      | Locked Out            |
      | Insufficient Evidence |
      | Failed Matching       |
      | Technical Issue       |
      | User Aborted          |
      | Timed Out             |