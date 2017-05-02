package uk.gov.hmrc.integration.cucumber.stepdefs

import cucumber.api.scala.{EN, ScalaDsl}
import uk.gov.hmrc.integration.cucumber.flows.LoginUsingGG
import uk.gov.hmrc.integration.cucumber.pages._
import uk.gov.hmrc.integration.cucumber.pages.generic.{BasePage, GovernmentGatewayPage}
import org.scalatest.Assertions._
import uk.gov.hmrc.integration.cucumber.pages.generic.{AuthorityWizardPage, GovernmentGatewayPage}



class HelpToSaveStep extends BasePage{

  Given("""^an applicant has a confidence level of (.*)$"""){ (level : String) =>
    println("Hello!!!")
    AuthorityWizardPage.goToAuthWizardPage()
    AuthorityWizardPage.redirect
    AuthorityWizardPage.confidenceLevel(level)
  }

  Given("""^their credential strength is (.*)$"""){ (strength : String) =>
    AuthorityWizardPage.credentialStrength(strength)
    AuthorityWizardPage.nino("JA553215D")
    AuthorityWizardPage.submit()
  }

  Then("""^they are forced into undergoing 2SV and CANNOT proceed with their HtS application$"""){ () =>
    val url = BasePage.getCurrentUrl
    url should include("one-time-password")
  }


}
