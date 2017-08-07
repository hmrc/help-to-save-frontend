package src.test.scala.hts.pages.registrationPages

import src.test.scala.hts.pages.WebPage

object ApplyPage extends WebPage {

  def clickStartNow(): Unit = click on xpath(".//*[@class='button button--get-started']")

}
