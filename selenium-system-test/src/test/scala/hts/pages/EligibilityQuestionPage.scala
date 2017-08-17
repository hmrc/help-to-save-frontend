package src.test.scala.hts.pages

object EligibilityQuestionPage extends WebPage {

  //TODO This page is still under construction
  val pageTitle = "\uD83D\uDE1E You don't have an account \uD83D\uDE22"

  def clickCheckEligibility(): Unit = click on "continue"

  override def isCurrentPage: Boolean = checkHeader("h2", pageTitle)
}
