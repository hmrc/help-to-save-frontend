Feature: Verifying header and footer links on every page

  @HTS-668 @VerifyHeaderAndFooter @HTS-1183
  Scenario: when users navigate through the HTS pages they see feedback, get-help and privacy links are working
    When the user has logged in and passed IV
    Then they create an account and can access all header and footer links
    And they manage their account and can access all header and footer links

  @VerifyHeaderAndFooter @HTS-1183
  Scenario: checking the Header and Footer links of the cannot change email try later page
    When the user has logged in and passed IV
    Then they can access the cannot change email try later page

  @VerifyHeaderAndFooter @HTS-1183
  Scenario: checking the Header and Footer links of the service unavailable/daily cap/total cap pages
    When an eligible applicant logs into gg
    Then they see the service unavailable page
    Then they see the daily cap reached page
    Then they see the total cap reached page

  @VerifyHeaderAndFooter @HTS-1183
  Scenario: checking the Header and Footer links of the missing details page
    When an applicant logs into gg with missing details
    Then they see a page showing which details are missing

  @VerifyHeaderAndFooter @HTS-1183
  Scenario: checking the Header and Footer links of the close account are you sure page
    When they log in and proceed to create an account using their GG email
    Then they navigate to and see the close account are you sure page

  @VerifyHeaderAndFooter
  Scenario: Checking links to HTS GOV.UK eligibility page
    Given they have gone through GG/2SV/identity check but they are NOT eligible for Help to Save
    When they click on eligibility for Help to Save link
    Then they are directed to the GOV.UK eligibility page
    When they click on eligibility criteria link
    Then they are directed to the GOV.UK eligibility page

  @VerifyHeaderAndFooter
  Scenario: Checking links to HTS GOV.UK page
    Given they apply for Help to Save
    When they click on Exit to GOV.UK link
    Then they are directed to the GOV.UK How it works page
    When they click on Cancel and go to GOV.UK link
    Then they are directed to the GOV.UK How it works page

  @VerifyHeaderAndFooter
  Scenario: Checking link to GG registration page
    Given an applicant who hasn't been through IV applies
    When they go through the IV journey and fail because of Precondition Failed
    And they click on Create a new GG account
    Then they are directed to the GG registration page