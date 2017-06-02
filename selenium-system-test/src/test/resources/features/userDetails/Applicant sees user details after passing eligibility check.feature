Feature: Applicant sees user details after passing eligibility check

  Scenario: Applicant sees user details
    Given an applicant has the following details:
      |field          |value                  |
      |name           |TestUser sureshSurname |
      |NINO           |AE553321D              |
      |date of birth  |11/12/1986             |
      |email address  |user@test.com          |

    When an applicant passes the eligibility check
    Then they see their details
