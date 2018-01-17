@AirGap
Feature: Air-gap tests for testing with NS&I

  Scenario: Air-gap 1 - All fields are populated
    Given an applicant has the following details:
      | field          | value           |
      | first name     | AirGap          |
      | last name      | One             |
      | NINO           | <eligible>      |
      | date of birth  | 12/12/1999      |
      | email address  | sarah@smith.com |
      | address line 1 | 1 the street    |
      | address line 2 | the place       |
      | address line 3 | the town        |
      | address line 4 | line 4          |
      | address line 5 | line 5          |
      | postcode       | BN43 5QP        |
      | country code   | GB              |

    When they choose to go ahead with creating an account
    Then they see that the account is created

  Scenario: Air-gap 2 - Only mandatory fields (in correct format) are populated
    Given an applicant has the following details:
      | field          | value         |
      | first name     | AirGap        |
      | last name      | Two           |
      | NINO           | <eligible>    |
      | date of birth  | 20/12/1980    |
      | email address  | user@test.com |
      | address line 1 | 1 the street  |
      | address line 2 | the place     |
      | postcode       | BN43 5QP      |

    When they choose to go ahead with creating an account
    Then they see that the account is created

  Scenario: Air-gap 3 - All fields populated with values of maximum length
    Given an applicant has the following details:
      | field          | value                                                                                                                                                                                                                                                                                                        |
      | first name     | AirGapKccGqrrfOOZyOsnpMWyu                                                                                                                                                                                                                                                                                   |
      | last name      | ThreevekAKPPqWOwAcqpPzUdXuPiGSlSSkZDTIoBfcmrGBxaYxvxHmLttFwCrxcdtSSYFwmUxgYnaLVBxoeawsbSmGBCoKYKVIZqSVkXxuubKUDmzSAROBiQnRuvViEZruyplAQJunCOTCxHXdZcGfREpLmleevJTdDskOEOUzrLbZrZmzQuALPwEGdBfbWmGmvIuUTMuHzUzsCPosEpGAhdElUzcnROuExrNnLQAGWpxtrFiLOqHAXktwjZGOirFUDSWmXzHidhYHtIuOAmxPbYnjGSIifLLzQqikzhlxxr |
      | NINO           | <eligible>                                                                                                                                                                                                                                                                                                   |
      | date of birth  | 12/12/1999                                                                                                                                                                                                                                                                                                   |
      | email address  | TsUuy7RJU2cLVKvoOkew7gSJ83VpyF4wteOOGAsFyb69e8Shqzg1BfeZPfH8xw4B@Nt4iqxK2YNdXrcoMgYhXhqeXwzEJTfs08i9oHouOLe3uzpIULnl47Lacupeurs0ag9gIPb1DyuMh8wQBUO6BFDvf0YtobaHjaE8JHq0fuSYgSdg4u4DA8E8xvKTjsAsEqgUeNtrC1WwpnnxauuvdWV0e4LGVcN3pzXX68HOWciM2JepL36yhex34yXIjR                                               |
      | address line 1 | RaEUvYCPYC9my5AfEwp5EepmqGNjKiNKHZs                                                                                                                                                                                                                                                                          |
      | address line 2 | oZeJ07Oqq5s4JHKjCTBjPLNPeuUyDEZRwoF                                                                                                                                                                                                                                                                          |
      | address line 3 | 12Qup9dvVFWSbl3SSVCMyurTYNOOwBUiRwe                                                                                                                                                                                                                                                                          |
      | address line 4 | cyKuQ2t7K6xV8vHJn6yAgchbQtWsiaYePxw                                                                                                                                                                                                                                                                          |
      | address line 5 | 70pVtjltRwwncMAp6810vimEzkToheldHhO                                                                                                                                                                                                                                                                          |
      | postcode       | BN435QPABC                                                                                                                                                                                                                                                                                                   |
      | country code   | GB                                                                                                                                                                                                                                                                                                           |

    When they choose to go ahead with creating an account
    Then they see that the account is created


  Scenario: Air-gap 4 - All fields are populated with values of minimum length
    Given an applicant has the following details:
      | field          | value      |
      | first name     | A          |
      | last name      | 4          |
      | NINO           | <eligible> |
      | date of birth  | 12/12/1999 |
      | email address  | a@a        |
      | address line 1 | a          |
      | address line 2 | b          |
      | address line 3 | c          |
      | address line 4 | d          |
      | address line 5 | e          |
      | postcode       | B          |
      | country code   | GB         |

    When they choose to go ahead with creating an account
    Then they see that the account is created


  Scenario: Air-gap 7 - Wacky postal address
    Given an applicant has the following details:
      | field          | value                    |
      | first name     | AirGap                   |
      | last name      | Seven                    |
      | NINO           | <eligible>               |
      | date of birth  | 12/12/1999               |
      | email address  | sarah@smith.com          |
      | address line 1 | C/O Fish 'n' Chips Ltd.  |
      | address line 2 | The Tate & Lyle Building |
      | address line 3 | Carisbrooke Rd.          |
      | address line 4 | Barton-under-Needwood    |
      | address line 5 | Derbyshire               |
      | postcode       | W1J7NT                   |
      | country code   | GB                       |

    When they choose to go ahead with creating an account
    Then they see that the account is created


  Scenario: Air-gap 11 - Populate forename, surname, address lines (phone number) and email with accented characters
    Given an applicant has the following details:
      | field          | value                    |
      | first name     | AirGapRené Chloë         |
      | last name      | ElevenO'Connor-Jørgensen |
      | NINO           | <eligible>               |
      | date of birth  | 12/12/1999               |
      | email address  | sarah@smith.com          |
      | address line 1 | 17 Ålfotbreen            |
      | address line 2 | Grünerløkka              |
      | address line 3 | Bodø                     |
      | address line 4 | Hørdy-Gürdy4             |
      | address line 5 | Hørdy-Gürdy5             |
      | postcode       | 19023                    |
      | country code   | SE                       |

    When they choose to go ahead with creating an account
    Then they see that the account is created