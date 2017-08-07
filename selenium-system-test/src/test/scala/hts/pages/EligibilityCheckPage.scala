package src.test.scala.hts.pages

import src.test.scala.hts.utils.Configuration.host

object EligibilityCheckPage extends WebPage {

  def startCreatingAccount(): Unit = click on xpath(".//*[@type='submit']")

}
