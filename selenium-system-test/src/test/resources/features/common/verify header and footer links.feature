Feature: Logging in

  @HTS-668
  Scenario: when users navigates through the HTS pages they see feedback, get-help and privacy links are working
    Given that users are authenticated
    When  they are at the start of the hts pages
    Then  they see all feedback, get-help and privacy links are working as they go through the journey
