Feature: more ZAP tests to hit the rest of the URLS

  @zap
  Scenario Outline: Hit URIs for ZAP testing
    Given the user has logged in and passed IV
    When I call URI <URI>
    Then I see a valid response

    Examples:
      | URI                                  |
      | no-account                           |
      | check-eligibility                    |
      | not-eligible                         |
      | eligible                             |
      | incorrect-details                    |
      | missing-details                      |
      | select-email                         |
      | enter-email                          |
      | confirm-email/:email                 |
      | verify-email                         |
      | email-verified-callback              |
      | email-verified                       |
      | cannot-change-email                  |
      | cannot-change-email-try-later        |
      | email-updated                        |
      | create-account                       |
      | account-home/change-email            |
      | account-home/email-verified          |
      | account-home/email-verified-callback |
      | account-home/verify-email            |
      | error-no-account                     |