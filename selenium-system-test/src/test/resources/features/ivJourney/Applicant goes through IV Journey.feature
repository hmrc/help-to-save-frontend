Feature: Applicant goes through IV journey

  @iv-journey
  Scenario: An eligible applicant passes identity verification and eligibility check
    Given an applicant who hasn't been through identity verification is on the Apply page
    When they go through identity verification check successfully and continue
    Then they will be redirected to the eligibility check and pass it

  @iv-journey
  Scenario Outline: An eligible applicant does not pass identity verification and has to try again
    Given an applicant who hasn't been through identity verification is on the Apply page
    When they go through identity verification and fail because of <reason>
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