package src.test.scala.hts.pages.registrationPages

import src.test.scala.hts.pages.WebPage
import src.test.scala.hts.utils.Configuration.{host => host}

object ApplyPage extends WebPage {

  def clickStartNow(): Unit = click on xpath(".//*[@class='button button--get-started']")

  def clickSignInLink(): Unit = click on xpath(".//*[@class='service-info']/ul/li/a")

  def navigateToApplyPage(): Unit = go to s"$host/help-to-save/apply-for-help-to-save/apply"

}
