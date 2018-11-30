Feature: more ZAP tests to hit the rest of the URLS

  @zap @BrowserStack
  Scenario: Hit URIs for ZAP testing
    Given the user logs in and passes IV on a PC, phone or tablet
    Then if they visit the URIs below they see a valid response
      | no-account                              |
      | check-eligibility                       |
      | not-eligible                            |
      | eligible                                |
      | incorrect-details                       |
      | missing-details                         |
      | select-email                            |
      | enter-email                             |
      | confirm-email/user@test.com             |
      | confirm-email                           |
      | email-confirmed-callback                |
      | email-confirmed                         |
      | cannot-change-email                     |
      | cannot-change-email-try-later           |
      | email-updated                           |
      | create-account                          |
      | account-home/change-email               |
      | account-home/email-verified             |
      | account-home/email-confirmed-callback   |
      | account-home/confirm-email              |
      | error-no-account                        |
      | account-home/close-account-are-you-sure |