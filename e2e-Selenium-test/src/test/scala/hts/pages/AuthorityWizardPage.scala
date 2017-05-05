package hts.pages

import cucumber.api.DataTable
import org.openqa.selenium.By
import hts.models.ResultsItem

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import util.Random

object AuthorityWizardPage extends Page {

//  def shouldContainAnRnrbAmountOf(amount: String) = {
//    webDriver.findElement(By.className("data-item")).getText shouldBe amount
//  }
//
//  def shouldContainResults(dataTable: DataTable) = {
//    val expectedResults = dataTable.asList[ResultsItem](classOf[ResultsItem]).asScala
//    val listElements = webDriver.findElements(By.cssSelector(".tabular-data__entry")).map {
//      element => {
//        val children = element.findElements(By.tagName("div"))
//        ResultsItem(children.get(0).getText, children.get(1).getText)
//      }
//    }
//    for (expectedResult <- expectedResults) {
//      listElements should contain(expectedResult)
//    }
//  }
//
//  def clickReveal = {
//    webDriver.findElement(By.tagName("summary")).click()
//  }

  def goToPage = driver.navigate().to("https://www-dev.tax.service.gov.uk/auth-login-stub/gg-sign-in")

  def credentials = driver.findElement(By.name("authorityId")).sendKeys(Random.nextInt(999999).toString)

  def redirect(url : String) = driver.findElement(By.name("redirectionUrl")).sendKeys(url)

  def nino(number : String) = driver.findElement(By.name("nino")).sendKeys(number)

  def credentialStrength(strength : String) = driver.findElement(By.name("credentialStrength")).sendKeys(strength)

  def confidenceLevel(level : Int) = driver.findElement(By.name("confidenceLevel")).sendKeys(level.toString)

  def submit = driver.findElement(By.cssSelector("input.button")).click
}
