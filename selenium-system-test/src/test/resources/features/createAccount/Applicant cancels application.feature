@HTS-431
Feature: Applicant cancels application

  @zap
  Scenario: Applicant cancels application
    When an applicant cancels their application just before giving the go-ahead to create an account
    Then they see the Help to Save About page
