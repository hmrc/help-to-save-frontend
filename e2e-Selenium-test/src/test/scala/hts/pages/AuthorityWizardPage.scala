/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package hts.pages

import org.openqa.selenium.By
import util.Random

object AuthorityWizardPage extends Page {

  // TODO: read URI from config
  def goToPage: Unit = driver.navigate().to("https://www-dev.tax.service.gov.uk/auth-login-stub/gg-sign-in")

  def credId: Unit = driver.findElement(By.name("authorityId")).sendKeys(Random.nextInt(999999).toString)

  def redirect(url : String): Unit = driver.findElement(By.name("redirectionUrl")).sendKeys(url)

  def nino(number : String): Unit = driver.findElement(By.name("nino")).sendKeys(number)

  def credentialStrength(strength : String): Unit = driver.findElement(By.name("credentialStrength")).sendKeys(strength)

  def confidenceLevel(level : Int): Unit = driver.findElement(By.name("confidenceLevel")).sendKeys(level.toString)

  def submit: Unit = driver.findElement(By.cssSelector("input.button")).click
}
