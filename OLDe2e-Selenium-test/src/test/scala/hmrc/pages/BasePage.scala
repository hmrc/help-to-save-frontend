package hmrc.pages

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util
import java.util.concurrent.TimeUnit

import cucumber.api.scala.{EN, ScalaDsl}
import org.junit.Assert
import org.openqa.selenium._
import org.openqa.selenium.support.ui.{ExpectedConditions, FluentWait, Wait}
import org.scalatest.selenium.WebBrowser
import org.scalatest.{Assertion, Inside, Matchers}
import hmrc.utils.BaseUtil._
import hmrc.utils.{Env, SingletonDriver}

object BasePage extends BasePage
trait BasePage extends Matchers
  with Inside
  with org.scalatest.selenium.Page
  with EN
  with ScalaDsl
  with WebBrowser {
  implicit val driver: WebDriver = SingletonDriver.getInstance()

  protected val DATE_FORMATTER_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("dd")
  protected val DATE_FORMATTER_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("MM")
  protected val DATE_FORMATTER_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy")
  protected val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMMM yyyy")

  def isCurrentPage: Boolean = false
  override val url: String = ""

  lazy val basePageUrl: String = s"${Env.baseUrl}/help-to-save/register/declaration"
  lazy val baseAuthWizardPageUrl: String = s"${Env.baseAuthWizUrl}/auth-login-stub/gg-sign-in"

  def waitForPageToBeLoaded(condition: => Boolean, exceptionMessage: String, timeoutInSeconds: Int = 30) {
    val endTime = System.currentTimeMillis + timeoutInSeconds * 1000

    while (System.currentTimeMillis < endTime) {
      try {
        if (condition) {
          return
        }
      } catch {
        case _: RuntimeException =>
        // ignore exceptions during the timeout period because the condition
        // is throwing exceptions and we DO want to try the condition again until the timeout expires
      }
      Thread.sleep(500)
    }
    throw new HmrcPageWaitException(exceptionMessage + "\n@@@@@@@@@ The current page was: " + driver.getCurrentUrl + " with title " + driver.getTitle)
  }

  var fluentwait: Wait[WebDriver] = new FluentWait[WebDriver](driver)
    .withTimeout(20, TimeUnit.SECONDS)
    .pollingEvery(100, TimeUnit.MILLISECONDS)

  def waitForElementPresent(locator: By) {
    fluentwait.until(ExpectedConditions.presenceOfElementLocated(locator))
  }
  def waitForElementVisible(locator: By) {
    fluentwait.until(ExpectedConditions.visibilityOfElementLocated(locator))
  }

  class HmrcPageWaitException(exceptionMessage: String) extends Exception(exceptionMessage)

  def verifyValueUsingElementId(elementId: String, expectedValue: String) : Boolean = driver.findElement(By.id(elementId)).getText == expectedValue

  def secondsWait(secs: Int): Unit = Thread.sleep(secs.*(1000))

  // Page Helpers
  def LABEL(id: String) = s"label[for='$id']"

  def changeLinkVerification(selector: String): WebElement = driver.findElement(By.cssSelector(selector))

  def changeLinkClick(linkText: String): Unit = driver.findElement(By.cssSelector("a[title*=\"" + linkText + "\"]")).click()

  def verifyLink(linkText: String): WebElement = driver.findElement(By.cssSelector("a[title*=\"" + linkText + "\"]"))


  def checkText(fieldValue: String, id: String): Unit = Assert.assertEquals(fieldValue, findById(id).getText)

  def helpLink(): WebElement = findById("get-help-action")

  def back(): Unit = driver.navigate().back()

  def pageRefresh(): Unit = {
    driver.navigate().refresh()
    try {
      val alert = driver.switchTo().alert()
      alert.accept()
    }
    catch {
      case noapEx: NoAlertPresentException => {}
    }
  }

  def validateErrorMessages(field: String, messageKey: String): Assertion = {
    val errorMessage = findById(field + "-error-summary").getText
    errorMessage shouldBe getMessage(messageKey)
  }

  def incorpId : Boolean = findById("incorpID").isDisplayed

  def findById(id: String): WebElement = driver.findElement(By.id(id))

  def findByName(id: String): util.List[WebElement] = driver.findElements(By.name(id))

  def checkForText(text: String): Boolean = driver.getPageSource.contains(text)

  def clickById(id: String): Unit = findById(id).click()

  def clickByName(id: String, num: Int): Unit = findByName(id).get(num).click()

  def sendKeysById(id: String, value: String): Unit = {
    findById(id).clear()
    findById(id).sendKeys(value)
  }

  def sendKeysByName(id: String, index: Int, value: String): Unit = {
    findByName(id).clear()
    findByName(id).get(index).sendKeys(value)
  }

  // Navigation through pages
  def getMessage(key: String): String = getProperty(key, props).replaceAll("''", "'")

  def clickSubmit(): Unit = findById("next").click()

  def clickNext(): Unit = findById("next").click()

  def clickStartNow(): Unit = findById("next").click()

  def bodyTitle(): Option[String] = find(tagName("h1")).map(_.text)

  def bodyPreText(): Option[String] = find(tagName("pre")).map(_.text)

  def clickSaveAndContinue(): Unit = click on id("save-and-continue")

  def clickContinue(): Unit = click on id("save-and-continue")

  def formattedDate(localDate: LocalDate)(implicit dateFormatter: DateTimeFormatter):String = localDate.format(dateFormatter)

  def checkBodyTitle(title: String): Boolean = {
    bodyTitle() shouldBe Some(title)
    bodyTitle() contains title
  }

  def checkBodyPreText(text: String): Boolean = {
    bodyPreText() shouldBe Some(text)
    bodyPreText() contains text
  }

  def clickOptionYesNo(option: String, radioButtonGroup: RadioButtonGroup, idYes: String, idNo: String): Unit = option match {
    case "Yes" => radioButtonGroup.value = idYes
    case "No"  => radioButtonGroup.value = idNo
  }

  def checkOptionYesNo(option: String, radioButtonGroup: RadioButtonGroup, idYes: String, idNo: String): Assertion = option match {
    case "Yes" => radioButtonGroup.value shouldBe idYes
    case "No"  => radioButtonGroup.value shouldBe idNo
  }

  def goToS4lTeardown(): Unit = {
    go to s"$basePageUrl/test-only/s4l-teardown"
    BasePage.waitForPageToBeLoaded(checkBodyPreText("Save4Later cleared"), "Failed to clear S4L")
  }

  def goToDbTeardown(): Unit = {
    go to s"$basePageUrl/test-only/db-teardown"
    BasePage.waitForPageToBeLoaded(checkBodyPreText("DB cleared"), "Failed to clear DB")
  }

  def goToCurrentProfileSetup(): Unit = {
    go to s"$basePageUrl/test-only/current-profile-setup"
    BasePage.waitForPageToBeLoaded(checkBodyPreText("Current profile setup"), "Failed to setup current profile")
  }

  def goToStartPage(): Unit = {
    go to s"$basePageUrl/start"
    BasePage.waitForPageToBeLoaded(checkBodyTitle(getMessage("start.body.header")), "Failed to load start page")
  }

  def goToSummaryPage(): Unit = {
    go to s"$basePageUrl/summary"
    BasePage.waitForPageToBeLoaded(checkBodyTitle(getMessage("summary.body.header")), "Failed to load start page")
  }

  def getCurrentUrl = driver.getCurrentUrl
}