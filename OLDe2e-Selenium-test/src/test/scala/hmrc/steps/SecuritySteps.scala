package hmrc.steps

import hmrc.pages.{AuthorityWizardPage, BasePage}
import AuthorityWizardPage._

class SecuritySteps extends BasePage {

  Given("""^an applicant has a confidence level of (.*)$"""){ (level : String) =>
    goToAuthWizardPage
    redirect
    confidenceLevel(level)
  }

  Given("""^their confidence level is (.*)$"""){ (level : String) =>
    AuthorityWizardPage.redirect
    AuthorityWizardPage.confidenceLevel(level)
    AuthorityWizardPage.submit
  }

  Given("""^their credential strength is (.*)$"""){ (strength : String) =>
    credentialStrength(strength)
    nino("JA553215D")
    submit
  }

<<<<<<< HEAD
  Then("""^they are forced into going through 2SV and CANNOT proceed with their HtS application$"""){ () =>
    BasePage.getCurrentUrl should include("one-time-password")
=======
  Given("""^an applicant's credential strength is (.*)$"""){ (strength : String) =>
    AuthorityWizardPage.goToAuthWizardPage
    AuthorityWizardPage.credentialStrength(strength)
    AuthorityWizardPage.nino("JA553215D")
  }

  Then("""^they are forced into going through 2SV before being able to proceed with their HtS application$"""){ () =>
    val url = BasePage.getCurrentUrl
    url should include("one-time-password")
>>>>>>> Saving changes
  }

  Then("""^they are forced into going through IV before being able to proceed with their HtS application$"""){ () =>
    val url = BasePage.getCurrentUrl
    url should include("identity-verification")
  }
}
