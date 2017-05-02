package uk.gov.hmrc.integration.cucumber.pages.vatTradingDetails.vatChoice

import java.time.LocalDate

import org.scalatest.Assertion
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage

object StartDatePage extends BasePage{

  override val url: String = s"$basePageUrl/start-date"

  def startDateDay: TextField = textField("startDate.day")
  def startDateMonth: TextField = textField("startDate.month")
  def startDateYear: TextField = textField("startDate.year")

  def startDateRadioButton: RadioButtonGroup = radioButtonGroup("startDateRadio")

  def checkBodyTitle(): Boolean = checkBodyTitle(getMessage("sdp.body.header"))

  var startDate: String = ""
  var days = 0

  def initialise(): Unit = {
    startDate = getMessage("sdp.compRegDate")
    days = 0
  }

  def clickUseDateWhenRegistered(): Unit = {
    startDateRadioButton.value = "COMPANY_REGISTRATION_DATE"
    startDate = getMessage("sdp.compRegDate")
  }


  def clickUseWhenBusinessStartDate(): Unit = {
    startDateRadioButton.value = "BUSINESS_START_DATE"
    startDate =
      find(xpath("//label[@for='startDateRadio-business_start_date']")).collect{
        case element if element.text.contains(":")  =>
          element.text.substring( element.text.indexOf(":") + 1).trim
      }.getOrElse("")
  }

  def clickProvideFutureDate(): Unit = startDateRadioButton.value = "SPECIFIC_DATE"

  def checkUseDateWhenRegistered(): Assertion = startDateRadioButton.value shouldBe "COMPANY_REGISTRATION_DATE"

  def checkSpecificStartDate(): Assertion = {
    startDateRadioButton.value shouldBe "SPECIFIC_DATE"
    startDateDay.value shouldBe LocalDate.now().plusDays(days).format(DATE_FORMATTER_DAY).replaceFirst("^0", "")
    startDateMonth.value shouldBe LocalDate.now().plusDays(days).format(DATE_FORMATTER_MONTH).replaceFirst("^0", "")
    startDateYear.value shouldBe LocalDate.now().plusDays(days).format(DATE_FORMATTER_YEAR)
  }

  def provideDifferentDate(): Unit = {
    val d = if(days > 84) days - 5 else days + 5
    val newDate = LocalDate.now().plusDays(d)
    val differentDate = newDate.format(DATE_FORMATTER).replaceFirst("^0", "")

    startDateDay.value = newDate.format(DATE_FORMATTER_DAY)
    startDateMonth.value = newDate.format(DATE_FORMATTER_MONTH)
    startDateYear.value = newDate.format(DATE_FORMATTER_YEAR)

    startDate = differentDate
  }

  def provideFutureStartDate(daysInFuture: Int): Unit = {
    val newDate = LocalDate.now().plusDays(daysInFuture)
    val futureDate = newDate.format(DATE_FORMATTER).replaceFirst("^0", "")

    startDateDay.value = newDate.format(DATE_FORMATTER_DAY)
    startDateMonth.value = newDate.format(DATE_FORMATTER_MONTH)
    startDateYear.value = newDate.format(DATE_FORMATTER_YEAR)

    days = daysInFuture
    startDate = futureDate
  }

  def providePastStartDate(): Unit = {
    startDateDay.value = "20"
    startDateMonth.value = "06"
    startDateYear.value = "2016"
  }

  def checkOptionWhenRegistered(): Assertion =  find(cssSelector("""input[type="radio"][value="COMPANY_REGISTRATION_DATE"]""")) shouldBe 'defined
  def checkOptionFutureDate(): Assertion =  find(cssSelector("""input[type="radio"][value="SPECIFIC_DATE"]""")) shouldBe 'defined

  def checkRadioError(): Unit = validateErrorMessages("startDateRadio", "error.startDate.noSelection")
  def checkStartDateEmptyError(): Unit = validateErrorMessages("startDate", "error.startDate.empty")
  def checkDateLessThan2WorkingDaysError(): Unit = validateErrorMessages("startDate", "error.dateLessThan2WorkingDays")
  def checkDateAfter3MonthsError(): Unit = validateErrorMessages("startDate", "error.dateAfter3Months")

}
