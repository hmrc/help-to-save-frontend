package uk.gov.hmrc.integration.cucumber.stepdefs.vatContact.vatDigitalContacts

import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.vatContact.vatDigitalContact.BusinessContactDetailsPage


class BusinessContactDetails extends BasePage {

  When("""^I am presented with the business contact details page$""") { () =>
    go to BusinessContactDetailsPage
    BusinessContactDetailsPage.checkBodyTitle()
  }

  And("""^I provide the email address and the daytime phone number on business contact page$""") { () =>
    BusinessContactDetailsPage.provideEmailAddress()
    BusinessContactDetailsPage.provideDaytimePhoneNo()
  }

  And("""^I provide the email address and the mobile number on business contact page$""") { () =>
    BusinessContactDetailsPage.provideEmailAddress()
    BusinessContactDetailsPage.provideMobileNo()
  }

  And("""^I provide the email address, mobile number and email address on business contact page$""") { () =>
    BusinessContactDetailsPage.provideEmailAddress()
    BusinessContactDetailsPage.provideDaytimePhoneNo()
    BusinessContactDetailsPage.provideMobileNo()
  }

  And("""^I provide the email address, daytime phone number, mobile number and website address on business contact page$"""){ () =>
    BusinessContactDetailsPage.provideEmailAddress()
    BusinessContactDetailsPage.provideDaytimePhoneNo()
    BusinessContactDetailsPage.provideMobileNo()
    BusinessContactDetailsPage.provideWebAddress()
  }


  Then("""^I will be presented with the business contact details page$""") { () =>
    BusinessContactDetailsPage.checkBodyTitle()
  }

  And("""^I provide invalid character for the email address, daytime phone and mobile no on the business contact page$""") { () =>
    BusinessContactDetailsPage.provideInvalidCharacters()
  }

  Then("""^I will see the error messages for the respective field on the business contact page$""") { () =>
    BusinessContactDetailsPage.checkEmailAddressInvalidError()
    BusinessContactDetailsPage.checkDaytimePhoneInvalidError()
    BusinessContactDetailsPage.checkMobileNoInvalidError()
  }

  Then("""^I will see the error message "Enter an email address" on the business contact details page$""") { () =>
    BusinessContactDetailsPage.checkEmailAddressEmptyError()
  }

  And("""^I provide the email address but no phone numbers on the business contact page$""") { () =>
    BusinessContactDetailsPage.provideEmailAddress()
  }

  Then("""I will see the error message "Enter at least one phone number" on the business contact page""") { () =>
    BusinessContactDetailsPage.checkBusinessContactEmptyError()
  }


  //R
  And("""^The '(.*)' will be pre-populated$"""){ (prepopulateOption: String)=>
    // BusinessContactDetailsPage.checkEmailAddress()
    BusinessContactDetailsPage.checkPrePopulateCompanyContactOption(prepopulateOption)
  }

  And("""^The business daytime phone number will be pre-populated$"""){ ()=>
    BusinessContactDetailsPage.checkDaytimePhoneNo()
  }

  And("""^The business mobile number will be pre-populated$"""){ ()=>
    BusinessContactDetailsPage.checkMobileNo()
  }

  And("""^The business website address will be pre-populated$"""){ ()=>
    BusinessContactDetailsPage.checkWebAddress()
  }


  And("""^I provide the different '(.*)'$"""){ (differentContactOption: String)=>
    // BusinessContactDetailsPage.changeEmailAddress()
    BusinessContactDetailsPage.provideDifferentCompanyContactOption(differentContactOption)
  }

  And("""^I provide the different daytime phone number$"""){ ()=>
    BusinessContactDetailsPage.changeDaytimePhoneNo()
  }



}
