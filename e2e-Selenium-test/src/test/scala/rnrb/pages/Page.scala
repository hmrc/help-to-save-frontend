package hts.pages

import cucumber.api.DataTable
import org.joda.time.LocalDate
import org.openqa.selenium.support.ui.{ExpectedCondition, ExpectedConditions, WebDriverWait}
import org.openqa.selenium.{By, WebDriver}
import org.scalatest._
import hts.driver.StartUpTearDown
import hts.models.ResultsItem
import hts.utils.{Configuration, UrlHelper}
import hts.utils.StringHelper._

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

object Page extends Page

trait Page extends StartUpTearDown with Matchers with ShouldMatchers {
  implicit val webDriver: WebDriver = driver

  def pageTitle = webDriver.getTitle

  protected def waitFor(predicate: WebDriver => Boolean): Boolean = {
    new WebDriverWait(webDriver, Configuration.settings.timeout).until {
      new ExpectedCondition[Boolean] {
        override def apply(wd: WebDriver) = predicate(wd)
      }
    }
  }

  protected def waitForElement(by: By) = {
    new WebDriverWait(webDriver, Configuration.settings.timeout).until {
      ExpectedConditions.presenceOfElementLocated(by)
    }
  }

  def waitForUrlChange(prettyUrl: String) = {
    val url = UrlHelper.getFullUrl(prettyUrl)
    waitFor(wd => wd.getCurrentUrl != url)
  }

  //region Methods to click links, buttons etc.

  def click(by: By) = webDriver.findElement(by).click()

  def clickById(id: String) = click(By.id(id))

  def clickByCss(selector: String) = click(By.cssSelector(selector))

  def clickByLinkText(linkText: String) = click(By.linkText(linkText))

  def clickSubmit() = click(By.id("submit"))

  //endregion

  //region Methods to fill in text boxes, selects etc.

  def fillInput(by: By, text: String) = {
    val input = webDriver.findElement(by)
    input.clear()
    if (text != null && text.nonEmpty) input.sendKeys(text)
  }

  def fillInputById(id: String, text: String) = fillInput(By.id(id), text)

  def fillInputByCss(selector: String, text: String) = fillInput(By.cssSelector(selector), text)

  def fillDate(day: String, month: String, year: String): Unit = {
    fillInputById("day", day)
    fillInputById("month", month)
    fillInputById("year", year)
  }

  def fillDate(date: LocalDate): Unit = {
    fillInputById("day", date.getDayOfMonth.toString)
    fillInputById("month", date.getMonthOfYear.toString)
    fillInputById("year", date.getYear.toString)
  }

  def fillDate(date: String): Unit = {
    val pattern = """(.*)/(.*)/(.*)""".r
    val pattern(day, month, year) = date
    fillDate( day, month, year)
  }

  //endregion

  //region Assertions about a page

  def urlShouldMatch(prettyUrl: String) = {
    val convertedUrl = UrlHelper.convertToUrlSlug(prettyUrl)
    waitFor(wd => wd.getCurrentUrl.toLowerCase.endsWith(convertedUrl))
  }

  def shouldContain(text: String) = {
    waitFor(wd => wd.getPageSource.contains(text))
  }

  def shouldContain(dataTable: DataTable) = {
    val expectedResults = dataTable.asList[ResultsItem](classOf[ResultsItem]).asScala
    val listElements = webDriver.findElements(By.cssSelector(".tabular-data__entry")).map {
      element => {
        val children = element.findElements(By.tagName("div"))
        ResultsItem(children.get(0).getText, children.get(1).getText)
      }
    }
    for (expectedResult <- expectedResults) {
      listElements should contain(expectedResult)
    }
  }

  //endregion

  //region Navigation

//  def goToPage(page: String) = {
//    val url = UrlHelper.getFullUrl(page)
//    if (driver.getCurrentUrl != url) {
//      driver.navigate().to(url)
//      urlShouldMatch(page)
//    }
//  }

  //endregion Navigation

  //region Whole page interactions

  def submitDatePage(page: String, day: String, month: String, year: String) = {
    urlShouldMatch(page)
    fillDate(day, month, year)
    clickSubmit()
  }

  def submitValuePage(page: String, value: String) = {
    urlShouldMatch(page)
    fillInputById("value", stripCommas(value))
    clickSubmit()
  }

  def submitYesNoPage(page: String, answer: String) = {
    urlShouldMatch(page)
    clickById(answer.toLowerCase)
    clickSubmit()
  }

  def checkPageAndClickLink(page: String, linkText: String) = {
    urlShouldMatch(page)
    clickByLinkText(linkText)
  }

  def getCurrentUrl = webDriver.getCurrentUrl

  //endregion Whole page interactions
}
