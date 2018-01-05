@HTS-431
Feature: Applicant cancels application

  @zap @BrowserStack
  Scenario: Applicant cancels application
    When an applicant cancels their application just before creating an account
    Then they see the Help to Save About page
