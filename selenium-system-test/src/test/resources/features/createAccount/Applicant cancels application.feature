@HTS-431 @ignore
Feature: Applicant cancels application

  @zap
  Scenario: Applicant cancels application
    When an applicant cancels their application just before giving the go-ahead to create an account
    Then they see the Help to Save landing page (with information about Help to Save)
