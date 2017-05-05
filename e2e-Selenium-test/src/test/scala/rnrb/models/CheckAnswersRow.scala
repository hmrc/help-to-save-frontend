package rnrb.models

import org.openqa.selenium.{By, WebElement}

case class CheckAnswersRow (label: String, value: String)

object CheckAnswersRow {

  def apply(element: WebElement): CheckAnswersRow = {
    val cells = element.findElements(By.tagName("div"))
    CheckAnswersRow(cells.get(0).getText, cells.get(1).getText)
  }
}
