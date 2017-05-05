package hts.pages

import cucumber.api.DataTable
import org.openqa.selenium.By
import hts.models.CheckAnswersRow

import scala.collection.JavaConverters._

object CheckAnswersPage extends Page {

  def shouldContainRows(dataTable: DataTable) = {
    val expectedRows = dataTable.asList[CheckAnswersRow](classOf[CheckAnswersRow]).asScala
    val actualRows = webDriver.findElements(By.className("tabular-data__entry"))
      .asScala
      .map(element => CheckAnswersRow.apply(element))

    for (expectedRow <- expectedRows) assert(actualRows.contains(expectedRow))
  }
}
