@HTS-90
Feature: Applicant goes through eligibility check

  Scenario: User is in receipt of WTC and so is eligible
    Given a user is in receipt of WTC
    When they apply for Help to Save
    Then they see that they are eligible for Help to Save


  Scenario: User is entitled to WTC but NOT in receipt of WTC and NOT in receipt of UC and so is NOT eligible
    Given a user has NINO ZX368514A
    When they apply for Help to Save
    Then they see that they are NOT eligible for Help to Save with reason code 3
    When they then click on still think you're eligible link
    Then they see appeals and tax tribunal page

  Scenario: User is entitled to WTC but NOT in receipt of WTC and in receipt of UC but income is insufficient and so is NOT eligible
    Given a user has NINO EK978215B
    When they apply for Help to Save
    Then they see that they are NOT eligible for Help to Save with reason code 4
    When they then click on still think you're eligible link
    Then they see appeals and tax tribunal page

  Scenario: User is NOT entitled to WTC and in receipt of UC but income is insufficient and so is NOT eligible
    Given a user has NINO HR156614D
    When they apply for Help to Save
    Then they see that they are NOT eligible for Help to Save with reason code 5
    When they then click on still think you're eligible link
    Then they see appeals and tax tribunal page

  Scenario: User is NOT entitled to WTC and NOT in receipt of UC and so is NOT eligible
    Given a user has NINO LW634114A
    When they apply for Help to Save
    Then they see that they are NOT eligible for Help to Save with reason code 9
    When they then click on still think you're eligible link
    Then they see appeals and tax tribunal page

  @HTS-859
  Scenario: User experiences a technical error
    Given DES is down
    When they apply for Help to Save
    Then they see a technical error page