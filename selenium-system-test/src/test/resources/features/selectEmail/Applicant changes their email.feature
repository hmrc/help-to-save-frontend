Feature: Applicant changes their email

  Scenario: An eligible applicant chooses to use their GG email for hts
    Given the user has logged in and passed IV
    When they confirm their details and continue to create an account
    And they select their GG email and proceed
    Then they see the final Create Account page

  Scenario: An eligible applicant wants to give an email address
    Given HMRC doesn't currently hold an email address for the user
    When they start to create an account
    Then they are asked to enter an email address

  @HTS-400
  Scenario: Applicant requests a re-send of the verification email
    Given they've chosen to enter a new email address during the application process
    When they request a re-send of the verification email
    Then they are asked to check their email for a verification email

  @HTS-399
  Scenario: Applicant changes their mind about the new email they provided
    Given they've chosen to enter a new email address during the application process
    When they want to change their email again
    Then they are asked to check their email for a verification email

  @HTS-876 @zap
  Scenario: Applicant updates email address
    Given they've chosen to enter a new email address during the application process
    When they click on the email verification link
    Then they see that their email has been successfully verified

