package hts.pages

import cucumber.api.DataTable
import org.openqa.selenium.By
import hts.models.ResultsItem

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import util.Random

object AuthorityWizardPage extends Page {

  def goToPage = driver.navigate().to("https://www-dev.tax.service.gov.uk/auth-login-stub/gg-sign-in")

  def credId = driver.findElement(By.name("authorityId")).sendKeys(Random.nextInt(999999).toString)

  def redirect(url : String) = driver.findElement(By.name("redirectionUrl")).sendKeys(url)

  def nino(number : String) = driver.findElement(By.name("nino")).sendKeys(number)

  def credentialStrength(strength : String) = driver.findElement(By.name("credentialStrength")).sendKeys(strength)

  def confidenceLevel(level : Int) = driver.findElement(By.name("confidenceLevel")).sendKeys(level.toString)

  def submit = driver.findElement(By.cssSelector("input.button")).click
}
