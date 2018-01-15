Feature: Providing journeys for testing cross-browser compatibility on different OS/browser combinations

  @BrowserStack
  Scenario: Going through happy path
    Given the user has logged in and passed IV
    Then  they go through the happy path