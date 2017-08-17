package src.test.scala.hts.pages

object EligiblePage extends WebPage {

  val pageTitle = "You're eligible"

  override def isCurrentPage: Boolean = checkHeader("h1", pageTitle)

}
