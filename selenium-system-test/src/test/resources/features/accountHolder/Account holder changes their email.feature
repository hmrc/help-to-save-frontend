Feature: Account holder changes their email

  @HTS-876 @VerifyHeaderAndFooter @HTS-1183 @wip
  Scenario: Account holder updates email address
    Given the account holder has chosen to enter a new email address
    When the account holder clicks on the email verification link
    Then the account holder sees that their email has been successfully verified

