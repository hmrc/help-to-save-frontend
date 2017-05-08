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

import org.openqa.selenium.support.ui.{ExpectedCondition, WebDriverWait}
import org.openqa.selenium.{By, WebDriver}
import org.scalatest._
import hts.driver.StartUpTearDown
import hts.utils.Configuration

object Page extends Page

trait Page extends StartUpTearDown with Matchers {

  def pageTitle = driver.getTitle

  protected def waitFor(predicate: WebDriver => Boolean): Boolean = {
    new WebDriverWait(driver, Configuration.settings.timeout).until {
      new ExpectedCondition[Boolean] {
        override def apply(wd: WebDriver) = predicate(wd)
      }
    }
  }

  //region Methods to click links, buttons etc.

  def click(by: By) = driver.findElement(by).click()

  def clickById(id: String) = click(By.id(id))

  def clickByLinkText(linkText: String) = click(By.linkText(linkText))

  def clickSubmit() = click(By.id("submit"))

  //endregion

  //region Methods to fill in text boxes, selects etc.

  def fillInput(by: By, text: String): Unit = {
    val input = driver.findElement(by)
    input.clear()
    if (text != null && text.nonEmpty) input.sendKeys(text)
  }

  def fillInputById(id: String, text: String): Unit = fillInput(By.id(id), text)


  //endregion

  //region Whole page interactions
  def getCurrentUrl = driver.getCurrentUrl

  //endregion Whole page interactions
}
