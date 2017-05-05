package hts.steps

import hts.pages.{Page, AuthorityWizardPage}

class SecuritySteps extends Steps {

  Given("^an applicant has a confidence level of (.*)$") { (level: Int) =>
    AuthorityWizardPage.goToPage
    AuthorityWizardPage.credentials
    AuthorityWizardPage.redirect("https://www-dev.tax.service.gov.uk/help-to-save/register/declaration")
    AuthorityWizardPage.confidenceLevel(level)
  }

  Given("""^their credential strength is (.*)$"""){ (strength : String) =>
    AuthorityWizardPage.credentialStrength(strength)
    AuthorityWizardPage.nino("JA553215D")
    AuthorityWizardPage.submit
  }

  Then("""^they are forced into going through 2SV before being able to proceed with their HtS application$"""){ () =>
    Page.getCurrentUrl should include("one-time-password")
  }

  Given("""^an applicant's credential strength is (.*)$"""){ (strength : String) =>
    AuthorityWizardPage.goToPage
    AuthorityWizardPage.credentials
    AuthorityWizardPage.credentialStrength(strength)
    AuthorityWizardPage.nino("JA553215D")
  }

  Given("""^their confidence level is (.*)$"""){ (level : Int) =>
    AuthorityWizardPage.redirect("https://www-dev.tax.service.gov.uk/help-to-save/register/declaration")
    AuthorityWizardPage.confidenceLevel(level)
    AuthorityWizardPage.submit
  }

  Then("""^they are forced into going through IV before being able to proceed with their HtS application$"""){ () =>
    Page.getCurrentUrl should include("identity-verification")
  }
}
