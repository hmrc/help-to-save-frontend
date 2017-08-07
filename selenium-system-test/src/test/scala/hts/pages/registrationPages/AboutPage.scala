package src.test.scala.hts.pages.registrationPages

import src.test.scala.hts.pages.WebPage
import src.test.scala.hts.utils.Configuration.host

object AboutPage extends WebPage {

  def navigateToAboutPage(): Unit = {
    go to s"$host/help-to-save/apply-for-help-to-save/about-help-to-save"
  }

}
