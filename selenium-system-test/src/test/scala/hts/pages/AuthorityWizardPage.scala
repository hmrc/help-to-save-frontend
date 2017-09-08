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
    AuthorityWizardPage.goToPage()
    fillInAuthDetails(redirectUrl, confidence, credentialStrength, nino)
  }

  private def fillInAuthDetails(redirectUrl: String, confidence: Int, credentialStrength: String, nino: String)(implicit driver: WebDriver): Unit = {
    setRedirect(redirectUrl)
    setConfidenceLevel(confidence)
    setCredentialStrength(credentialStrength)
    setNino(nino)
    setGivenName("GivenName")
    setFamilyName("FamilyName")
    setDateOfBirth("1980-12-20")
    setAddressLine1("AddressLine1")
    setAddressLine2("AddressLine2")
    setAddressLine3("AddressLine3")
    setAddressLine4("AddressLine4")
    setAddressLine5("AddressLine5")
    setPostCode("S24AH")
    setCountryCode("01")
    submit()
  }

  def goToPage()(implicit driver: WebDriver): Unit =
    go to s"${Configuration.authHost}/auth-login-stub/gg-sign-in"

  def setRedirect(url: String)(implicit driver: WebDriver): Unit =
    find(name("redirectionUrl")).foreach(_.underlying.sendKeys(url))

  def setNino(nino: String)(implicit driver: WebDriver): Unit =
    find(name("nino")).foreach(_.underlying.sendKeys(nino))

  def setCredentialStrength(strength: String)(implicit driver: WebDriver): Unit =
    find(name("credentialStrength")).foreach(_.underlying.sendKeys(strength))

  def setConfidenceLevel(level: Int)(implicit driver: WebDriver): Unit =
    find(name("confidenceLevel")).foreach(_.underlying.sendKeys(level.toString))

  def submit()(implicit driver: WebDriver): Unit =
    find(cssSelector("input.button")).foreach(_.underlying.click())

  def setGivenName(givenName: String)(implicit driver: WebDriver): Unit =
    find(name("itmp.givenName")).foreach(_.underlying.sendKeys(givenName))

  def setFamilyName(familyName: String)(implicit driver: WebDriver): Unit =
    find(name("itmp.familyName")).foreach(_.underlying.sendKeys(familyName))

  def setDateOfBirth(dateOfBirth: String)(implicit driver: WebDriver): Unit =
    find(name("itmp.dateOfBirth")).foreach(_.underlying.sendKeys(dateOfBirth))

  def setAddressLine1(addressLine1: String)(implicit driver: WebDriver): Unit =
    find(name("itmp.address.line1")).foreach(_.underlying.sendKeys(addressLine1))

  def setAddressLine2(addressLine2: String)(implicit driver: WebDriver): Unit =
    find(name("itmp.address.line2")).foreach(_.underlying.sendKeys(addressLine2))

  def setAddressLine3(addressLine3: String)(implicit driver: WebDriver): Unit =
    find(name("itmp.address.line3")).foreach(_.underlying.sendKeys(addressLine3))

  def setAddressLine4(addressLine4: String)(implicit driver: WebDriver): Unit =
    find(name("itmp.address.line4")).foreach(_.underlying.sendKeys(addressLine4))

  def setAddressLine5(addressLine5: String)(implicit driver: WebDriver): Unit =
    find(name("itmp.address.line5")).foreach(_.underlying.sendKeys(addressLine5))

  def setPostCode(postcode: String)(implicit driver: WebDriver): Unit =
    find(name("itmp.address.postCode")).foreach(_.underlying.sendKeys(postcode))

  def setCountryCode(countryCode: String)(implicit driver: WebDriver): Unit =
    find(name("itmp.address.countryCode")).foreach(_.underlying.sendKeys(countryCode))

}
