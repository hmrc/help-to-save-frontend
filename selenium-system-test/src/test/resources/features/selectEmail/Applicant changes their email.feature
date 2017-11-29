@current @done
Feature: Applicant changes their email

  @new
  Scenario: An eligible applicant chooses to use their GG email for hts
    Given a user has logged in and passed IV
    When they start to create an account
    Then they are shown a page to select which email address to use for hts
    When they select the email obtained from GG and click Continue
    Then they see the page "You're about to create a Help to Save account"

  @new @zap
  Scenario: An eligible applicant chooses to give a new email address for hts
    Given a user has logged in and passed IV
    When they start to create an account
    Then they are shown a page to select which email address to use for hts
    When they select I want to enter a new email address and enter a new email
    Then I see the page "You have 30 minutes to verify your email address"

  @new
  Scenario: An eligible applicant wants to give an email address
    Given HMRC doesn't currently hold an email address for me
    Then I am shown a page to enter my email address

  @HTS-400
  Scenario: Applicant requests a re-send of the verification email
    Given I've chosen to change my email address from A to B during the application process
    But I want to receive a second verification email
    When I request a re-send of the verification email
    Then I am asked to check my email account for a verification email

  @HTS-399
  Scenario: Applicant changes their mind about the new email they provided
    Given I've chosen to change my email address from A to B during the application process
    But I haven't yet verified new email address B
    When I then choose to change the email address from B to C
    Then I am asked to check my email account for a verification email
