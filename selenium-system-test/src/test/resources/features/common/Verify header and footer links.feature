Feature: Verifying header and footer links on every page

  @HTS-668
  Scenario: when users navigates through the HTS pages they see feedback, get-help and privacy links are working
    When the user has logged in and passed IV
    Then they go through the happy path they can see and access all header and footer links
