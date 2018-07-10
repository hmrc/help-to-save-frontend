Feature: Verifying header and footer links on every page

  @HTS-668 @VerifyHeaderAndFooter
  Scenario: when users navigate through the HTS pages they see feedback, get-help and privacy links are working
    When the user has logged in and passed IV
    Then they go through the happy path they can see and access all header and footer links

  @VerifyHeaderAndFooter
  Scenario: checking the Header and Footer links of the cannot change email try later page
    When the user has logged in and passed IV
    Then they can access the cannot change email try later page

  @VerifyHeaderAndFooter
  Scenario: checking the Header and Footer links of the sign in page
    When the user is not logged in
    Then they access the sign in page

  @VerifyHeaderAndFooter
  Scenario: checking the Header and Footer links of the service unavailable/daily cap/total cap pages
    When an eligible applicant logs into gg
    Then they see the service unavailable page
    Then they see the daily cap reached page
    Then they see the total cap reached page

  @VerifyHeaderAndFooter
  Scenario: checking the Header and Footer links of the missing details page
    When an applicant logs into gg with missing details
    Then they see a page showing which details are missing

   @VerifyHeaderAndFooter
   Scenario: checking the Header and Footer links of the close account are you sure page
    When they log in and proceed to create an account using their GG email
    Then they navigate to and see the close account are you sure page