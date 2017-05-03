package hmrc.steps

import hmrc.pages.{AuthorityWizardPage, BasePage}
import AuthorityWizardPage._

class SecuritySteps extends BasePage {

  Given("""^an applicant has a confidence level of (.*)$"""){ (level : String) =>
    goToAuthWizardPage
    redirect
    confidenceLevel(level)
  }

  Given("""^their credential strength is (.*)$"""){ (strength : String) =>
    credentialStrength(strength)
    nino("JA553215D")
    submit
  }

  Then("""^they are forced into going through 2SV and CANNOT proceed with their HtS application$"""){ () =>
    BasePage.getCurrentUrl should include("one-time-password")
  }
}
