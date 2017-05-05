package hmrc.flows

import org.scalatest.selenium.WebBrowser
import hmrc.pages.{AuthorityWizardPage, GovernmentGatewayPage}
import hmrc.utils.{Env, SingletonDriver}


object LoginUsingGG extends WebBrowser {

  implicit val driver = SingletonDriver.getInstance()

  def login(id: String): Unit = {
    deleteAllCookies()
    go to GovernmentGatewayPage
    if (Env.isQA(Env.baseUrl) || Env.isQA(currentUrl))  GovernmentGatewayPage.signIn(id)

  }
}
