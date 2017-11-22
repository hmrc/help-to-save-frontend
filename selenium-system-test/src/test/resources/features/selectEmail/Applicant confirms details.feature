@HTS-371 @HTS-379 @ignore
Feature: Applicant confirms details

  Scenario: Applicant confirms details
    Given an applicant has the following details:
      | field          | value         |
      | first name     | FirstName     |
      | last name      | LastName      |
      | NINO           | <eligible>    |
      | date of birth  | 20/12/1980    |
      | email address  | user@test.com |
      | address line 1 | 1 the street  |
      | address line 2 | the place     |
      | address line 3 | the place     |
      | address line 4 | the place     |
      | address line 5 | the place     |
      | postcode       | BN43 5QP      |
      | country code   | 01            |

    When an applicant passes the eligibility check
    Then they see their details