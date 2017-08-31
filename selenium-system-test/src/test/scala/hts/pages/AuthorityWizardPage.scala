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

import hts.utils.Configuration
import org.openqa.selenium.WebDriver

object AuthorityWizardPage extends WebPage {

  def authenticateUser(redirectUrl: String, confidence: Int, credentialStrength: String, nino: String)(implicit driver: WebDriver): Unit = {
    goToPage()
    fillInAuthDetails(redirectUrl, confidence, credentialStrength, nino)
  }

  private def fillInAuthDetails(redirectUrl: String, confidence: Int, credentialStrength: String, nino: String)(implicit driver: WebDriver): Unit = {
    setRedirect(redirectUrl)
    setConfidenceLevel(confidence)
    setCredentialStrength(credentialStrength)
    setNino(nino)
    setGivenName("a")
    setMiddleName("a")
    setFamilyName("a")
    setDateOfBirth("1980-12-20")
    setAddressLine1("a")
    setAddressLine2("a")
    setAddressLine3("a")
    setAddressLine4("a")
    setAddressLine5("a")
    setPostCode("S24AH")
    setCountryCode("01")
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

  def setGivenName(givenName: String)(implicit driver: WebDriver): Unit =
    find(name("itmp.givenName")).get.underlying.sendKeys(givenName)

  def setMiddleName(middleName: String)(implicit driver: WebDriver): Unit =
    find(name("itmp.middleName")).get.underlying.sendKeys(middleName)

  def setFamilyName(middleName: String)(implicit driver: WebDriver): Unit =
    find(name("itmp.familyName")).get.underlying.sendKeys(middleName)

  def setDateOfBirth(middleName: String)(implicit driver: WebDriver): Unit =
    find(name("itmp.dateOfBirth")).get.underlying.sendKeys(middleName)

  def setAddressLine1(middleName: String)(implicit driver: WebDriver): Unit =
    find(name("itmp.address.line1")).get.underlying.sendKeys(middleName)

  def setAddressLine2(middleName: String)(implicit driver: WebDriver): Unit =
    find(name("itmp.address.line2")).get.underlying.sendKeys(middleName)

  def setAddressLine3(middleName: String)(implicit driver: WebDriver): Unit =
    find(name("itmp.address.line3")).get.underlying.sendKeys(middleName)

  def setAddressLine4(middleName: String)(implicit driver: WebDriver): Unit =
    find(name("itmp.address.line4")).get.underlying.sendKeys(middleName)

  def setAddressLine5(middleName: String)(implicit driver: WebDriver): Unit =
    find(name("itmp.address.line5")).get.underlying.sendKeys(middleName)

  def setPostCode(middleName: String)(implicit driver: WebDriver): Unit =
    find(name("itmp.address.postCode")).get.underlying.sendKeys(middleName)

  def setCountryCode(middleName: String)(implicit driver: WebDriver): Unit =
    find(name("itmp.address.countryCode")).get.underlying.sendKeys(middleName)

}
