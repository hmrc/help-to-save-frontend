Feature: more ZAP tests to hit the rest of the URLS

  @zap
Scenario Outline: Hit URIs for ZAP testing
  Given a user has logged in and passed IV
  When I call URI <URI> with HTTP method <HTTP method>
  Then I see a response

Examples:
| URI                                | HTTP method |
| /access-account                    | GET         |
| /confirm-email/:email              | POST        |
| /create-an-account                 | POST        |
| /email/new-applicant-update        | POST        |
| /email/account-holder-update       | POST        |
| /email-verified                    | GET         |
| /account/update-your-email-address | GET         |
| /account/email-verified            | GET         |
| /account/email-updated             | GET         |
| /account/email-update-error        | GET         |

