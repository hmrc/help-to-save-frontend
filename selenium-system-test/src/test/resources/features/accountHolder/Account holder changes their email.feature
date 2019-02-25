Feature: Account holder changes their email

  @HTS-876 @VerifyHeaderAndFooter @HTS-1183
  Scenario: Account holder updates email address
    Given the account holder has chosen to enter a new email address
    When the account holder clicks on the email verification link
    Then the account holder sees that their email has been successfully verified

  @HTS-1614
  Scenario: Account holder with different NINO suffix updates email address
    Given the account holder has enrolled with NINO suffix C
    When the account holder logs in with suffix D
    And successfully updates their email address
    Then the account holder sees that their email has been successfully verified

