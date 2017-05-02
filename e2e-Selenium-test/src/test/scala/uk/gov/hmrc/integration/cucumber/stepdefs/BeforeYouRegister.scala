package uk.gov.hmrc.integration.cucumber.stepdefs

import cucumber.api.scala.{EN, ScalaDsl}
import uk.gov.hmrc.integration.cucumber.flows.LoginUsingGG
import uk.gov.hmrc.integration.cucumber.pages._
import uk.gov.hmrc.integration.cucumber.pages.generic.{BasePage, GovernmentGatewayPage}


class BeforeYouRegister extends BasePage{

  Given("""^I am an (.*) user$"""){ (id: String) =>
    LoginUsingGG.login(id)
  }

  Given("""^I access the start page$"""){ () =>
    go to BeforeYouRegisterPage
    BeforeYouRegisterPage.checkBodyTitle()
  }

  When("""^I attempt to access a VAT registration page directly$"""){ () =>
    go to BeforeYouRegisterPage
  }

  Then("""^I will be taken to the start page$"""){ () =>
    BeforeYouRegisterPage.checkBodyTitle()
  }

  And("""^I verify contents and submit from before you register page$"""){ () =>
    BeforeYouRegisterPage.verifyVatLinks()
    BeforeYouRegisterPage.clickContinue()
  }

  Then("""^I will be taken to sign in$"""){ () =>
    GovernmentGatewayPage.checkBodyTitle()
  }

  Then("""^I will be taken to the VAT start page$"""){ () =>
    BeforeYouRegisterPage.checkBodyTitle()
  }

  Then("""^when I sign in as an (.*) user$"""){ (id: String) =>
    LoginUsingGG.login(id)
  }

  Then("""^I will be presented with the Start page"""){ () =>
    BeforeYouRegisterPage.checkBodyTitle()
  }

}
