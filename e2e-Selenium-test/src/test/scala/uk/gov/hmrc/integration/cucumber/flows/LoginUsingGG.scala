package uk.gov.hmrc.integration.cucumber.flows

import org.scalatest.selenium.WebBrowser
import uk.gov.hmrc.integration.cucumber.pages.{AuthorityWizardPage, GovernmentGatewayPage}
import uk.gov.hmrc.integration.cucumber.utils.{Env, SingletonDriver}


object LoginUsingGG extends WebBrowser {

  implicit val driver = SingletonDriver.getInstance()

  def login(id: String): Unit = {
    deleteAllCookies()
    go to GovernmentGatewayPage
    if (Env.isQA(Env.baseUrl) || Env.isQA(currentUrl))  GovernmentGatewayPage.signIn(id)

  }
}
