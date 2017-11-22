@current
Feature: Applicant changes their email

  @HTS-33 @HTS-398 @zap
  Scenario: An applicant changes their email
    Given I am viewing my applicant details
    When I choose to change my email address
    Then I am asked to check my email account for a verification email

  @HTS-400
  Scenario: Applicant requests a re-send of the verification email
    Given I've chosen to change my email address from A to B during the application process
    But I want to receive a second verification email
    When I request a re-send of the verification email
    Then I am asked to check my email account for a verification email

  @HTS-399
  Scenario: Applicant changes their mind about the new address they provided
    Given I've chosen to change my email address from A to B during the application process
    But I haven't yet verified new email address B
    When I then choose to change the email address from B to C
    Then I am asked to check my email account for a verification email

  @new @done
  Scenario: An eligible applicant wants to give an email address
    Given HMRC doesn't currently hold an email address for me
    When they start to create an account
    Then I am shown a page to enter my email address

  @new
  Scenario: An eligible applicant chooses to use their GG email for hts
    Given a user has logged in and passed IV
    When they start to create an account
    Then I am shown a page to select which email address to use for hts
    When I select the email obtained from GG and click Continue
    //Im up to here
    Then I see the page "You're about to create a Help to Save account"

  @new
  Scenario: An eligible applicant chooses to give a new email address for hts
    Given a user has logged in and passed IV
    When they start to create an account
    Then they see a page where they can choose which email they want to use for HtS
    And the applicant selects I want to enter a new email address
    Then the page expands to allow the applicant to enter a new email address
    When the applicant enters a new email address and presses Continue
    Then applicant sees the page "You have 30 minutes to verify your email address"