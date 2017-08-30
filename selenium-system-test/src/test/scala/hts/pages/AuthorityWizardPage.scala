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

import java.util.Base64

import hts.utils.Configuration
import org.openqa.selenium.{By, WebDriver}

object AuthorityWizardPage extends WebPage {

  private val s: String = AuthorityWizardPage.encode(generateEligibleNINO)

  def getEncodedNino: String = s

  def authenticateUser(redirectUrl: String, confidence: Int, credentialStrength: String, nino: String)(implicit driver: WebDriver): Unit = {
    goToPage()
    fillInAuthDetails(redirectUrl, confidence, credentialStrength, nino)
  }

  private def fillInAuthDetails(redirectUrl: String, confidence: Int, credentialStrength: String, nino: String)(implicit driver: WebDriver): Unit = {
    setRedirect(redirectUrl)
    setConfidenceLevel(confidence)
    setCredentialStrength(credentialStrength)
    setNino(nino)
    submit()
  }

  def goToPage()(implicit driver: WebDriver): Unit =
    go to s"${Configuration.authHost}/auth-login-stub/gg-sign-in"

  def setRedirect(url: String)(implicit driver: WebDriver): Unit =
    find(name("redirectionUrl")).get.underlying.sendKeys(url)

  def setNino(nino: String)(implicit driver: WebDriver): Unit =
    find(name("nino")).get.underlying.sendKeys(nino)

  def setCredentialStrength(strength: String)(implicit driver: WebDriver): Unit =
    find(name("credentialStrength")).get.underlying.sendKeys(strength)

  def setConfidenceLevel(level: Int)(implicit driver: WebDriver): Unit =
    find(name("confidenceLevel")).get.underlying.sendKeys(level.toString)

  def submit()(implicit driver: WebDriver): Unit =
    find(cssSelector("input.button")).get.underlying.click()

  def encode(s: String): String = new String(
    Base64.getEncoder.encodeToString(s.getBytes()))

  def decode(s: String): String = new String(
    Base64.getDecoder.decode(s))

}
