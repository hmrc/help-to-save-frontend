package hts.pages

import org.openqa.selenium.By

object PropertyPassingToDirectDescendantsPage extends Page {

  val page = "property-passing-to-direct-descendants"

  lazy val allSelector = By.id("property_passing_to_direct_descendants.all")
  lazy val someSelector = By.id("property_passing_to_direct_descendants.some")
  lazy val noneSelector = By.id("property_passing_to_direct_descendants.none")

  def fillPage(answer: String) = {
    urlShouldMatch(page)
    val selector = answer match {
      case "Yes, all of it passed" => allSelector
      case "Yes, some of it passed" => someSelector
      case "No" => noneSelector
      case _ => throw new IllegalArgumentException
    }

    click(selector)
    clickSubmit()
  }
}
