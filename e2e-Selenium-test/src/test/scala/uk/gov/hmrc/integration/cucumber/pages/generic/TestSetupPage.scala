package uk.gov.hmrc.integration.cucumber.pages.generic

import java.time.LocalDate

import cucumber.api.DataTable
import uk.gov.hmrc.integration.cucumber.pages.vatTradingDetails.vatChoice.StartDatePage


object TestSetupPage extends BasePage {

  override val url: String = s"$basePageUrl/test-only/test-setup"

  def checkBodyTitle(): Boolean = checkBodyTitle("Test Setup")

  def checkBodyPreText(): Boolean = checkBodyPreText("Test setup complete")

  def enterData(data: DataTable): Unit = {
    val row = data.asMaps(classOf[String], classOf[String]).iterator
    while (row.hasNext) {
      val map = row.next
      val data = map.get("enteredData")
      val loc = map.get("S4Llocation")
      sendKeysById(loc, data)
    }
     if(getStartDateChoice == "SPECIFIC_DATE") provideFutureStartDate(7)
  }

  def startDateDay: TextField = textField("vatChoice.startDateDay")
  def startDateMonth: TextField = textField("vatChoice.startDateMonth")
  def startDateYear: TextField = textField("vatChoice.startDateYear")

  def startDateChoice: TextField = textField("vatChoice.startDateChoice")

  def getStartDateChoice: String = startDateChoice.value

  def provideFutureStartDate(daysInFuture: Int): Unit = {
    val futureDate = LocalDate.now().plusDays(daysInFuture).format(DATE_FORMATTER).replaceFirst("^0", "")

    startDateDay.value = LocalDate.now().plusDays(daysInFuture).format(DATE_FORMATTER_DAY)
    startDateMonth.value = LocalDate.now().plusDays(daysInFuture).format(DATE_FORMATTER_MONTH)
    startDateYear.value = LocalDate.now().plusDays(daysInFuture).format(DATE_FORMATTER_YEAR)

    StartDatePage.days = daysInFuture
    StartDatePage.startDate = futureDate
  }
}
