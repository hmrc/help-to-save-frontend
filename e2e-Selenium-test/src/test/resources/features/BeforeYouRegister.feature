#@vat
#  Feature: Before You Start
#
#    Scenario: Authenticated user accesses a page in VAT registration
#      Given I am an authenticated user
#      When I attempt to access a VAT registration page directly
#      Then I will be taken to the start page
#
#    Scenario: Verify links and contents in "before you register for VAT" page
#      Given I am an authenticated user
#      When I attempt to access a VAT registration page directly
#      Then I will be taken to the start page
#      And I verify contents and submit from before you register page
#
##      ToDo: Different levels of users not yet implemented
##    Scenario: Unauthenticated user accesses a page in VAT registration
##      Given I am an unauthenticated user
##      When I attempt to access a VAT registration page directly
##      Then I will be taken to sign in
##      And when I sign in as an authenticated user
##      Then I will be taken to the VAT start page
#
#    @AuthenticatedLogIn
#    Scenario: Navigation from the start page
#      Given I access the start page
#      When I continue
#      Then I will be presented with the >83K question