/*
 * Copyright 2019 HM Revenue & Customs
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

import java.time.format.DateTimeFormatter

import hts.browser.Browser
import hts.pages.registrationPages.EligiblePage
import hts.utils.{Configuration, TestUserInfo}
import org.openqa.selenium.{By, WebDriver}
import uk.gov.hmrc.helptosavefrontend.models.userinfo.Address

import scala.annotation.tailrec

object AuthorityWizardPage extends Page {

  override val expectedURL: String = s"${Configuration.authHost}/auth-login-stub/gg-sign-in"

  def authenticateUser(redirectUrl: String, confidence: Int, credentialStrength: String, nino: String)(implicit driver: WebDriver): Unit = {
    fillInAuthDetails(redirectUrl, confidence, credentialStrength, nino)
    clickSubmit()
  }

  def authenticateEligibleUser(redirectUrl: String, nino: String)(implicit driver: WebDriver): Unit = {
    usedNino = nino
    fillInAuthDetails(redirectUrl, 200, "strong", nino)
    clickSubmit()
    println(s"NINO Used = $usedNino \n")
  }

  def authenticateEligibleUser(redirectUrl: String)(implicit driver: WebDriver): Unit = {
    fillInAuthDetails(redirectUrl, 200, "strong", usedNino)
    clickSubmit()
  }

  def authenticateEligibleUserOnAnyDevice(redirectUrl: String, nino: String)(implicit driver: WebDriver): Unit = {
    fillInAuthDetails(redirectUrl, 200, "strong", nino)
    Browser.scrollToElement("button", By.className)
    clickSubmit()
  }

  def authenticateIneligibleUser(redirectUrl: String, nino: String)(implicit driver: WebDriver): Unit = {
    fillInAuthDetails(redirectUrl, 50, "none", nino)
    clickSubmit()
  }

  def authenticateUserNoEmail(redirectUrl: String, confidence: Int, credentialStrength: String, nino: String)(implicit driver: WebDriver): Unit = {
    fillInAuthDetails(redirectUrl, confidence, credentialStrength, nino)
    setBlankEmail
    clickSubmit()
  }

  def authenticateMissingDetailsUser(redirectUrl: String, nino: String)(implicit driver: WebDriver): Unit = {
    navigate()
    setRedirect(redirectUrl)
    setCredentialStrength("strong")
    setConfidenceLevel(200)
    setNino(nino)
    setDateOfBirth("1980-12-31")
    setAddressLine1("AddressLine1")
    setAddressLine2("AddressLine2")
    setAddressLine3("AddressLine3")
    setAddressLine4("AddressLine4")
    setAddressLine5("AddressLine5")
    setPostCode("S24AH")
    setCountryCode("GB-ENG")

    clickSubmit()
  }

  private def fillInAuthDetails(redirectUrl: String, confidence: Int, credentialStrength: String, nino: String)(implicit driver: WebDriver): Unit = {
    navigate()
    setRedirect(redirectUrl)
    setCredentialStrength(credentialStrength)
    setConfidenceLevel(confidence)
    setNino(nino)
    setGivenName("FirstName")
    setFamilyName("LastName")
    setDateOfBirth("1980-12-31")
    setAddressLine1("AddressLine1")
    setAddressLine2("AddressLine2")
    setAddressLine3("AddressLine3")
    setAddressLine4("AddressLine4")
    setAddressLine5("AddressLine5")
    setPostCode("S24AH")
    setCountryCode("GB-ENG")
  }

  def setRedirect(url: String)(implicit driver: WebDriver): Unit =
    Browser.find(Browser.name("redirectionUrl")).foreach(_.underlying.sendKeys(url))

  def setNino(nino: String)(implicit driver: WebDriver): Unit =
    Browser.find(Browser.name("nino")).foreach { element ⇒
      element.underlying.sendKeys(nino)
    }

  def setCredentialStrength(strength: String)(implicit driver: WebDriver): Unit =
    Browser.find(Browser.name("credentialStrength")).foreach(_.underlying.sendKeys(strength))

  def setConfidenceLevel(level: Int)(implicit driver: WebDriver): Unit =
    Browser.find(Browser.name("confidenceLevel")).foreach(_.underlying.sendKeys(level.toString))

  def setGivenName(givenName: String)(implicit driver: WebDriver): Unit =
    Browser.find(Browser.name("itmp.givenName")).foreach(_.underlying.sendKeys(givenName))

  def setFamilyName(familyName: String)(implicit driver: WebDriver): Unit =
    Browser.find(Browser.name("itmp.familyName")).foreach(_.underlying.sendKeys(familyName))

  def setDateOfBirth(dateOfBirth: String)(implicit driver: WebDriver): Unit =
    Browser.find(Browser.name("itmp.dateOfBirth")).foreach(_.underlying.sendKeys(dateOfBirth))

  def setBlankEmail(implicit driver: WebDriver): Unit =
    Browser.find(Browser.name("email")).foreach(_.underlying.clear())

  def setAddressLine1(addressLine1: String)(implicit driver: WebDriver): Unit =
    Browser.find(Browser.name("itmp.address.line1")).foreach(_.underlying.sendKeys(addressLine1))

  def setAddressLine2(addressLine2: String)(implicit driver: WebDriver): Unit =
    Browser.find(Browser.name("itmp.address.line2")).foreach(_.underlying.sendKeys(addressLine2))

  def setAddressLine3(addressLine3: String)(implicit driver: WebDriver): Unit =
    Browser.find(Browser.name("itmp.address.line3")).foreach(_.underlying.sendKeys(addressLine3))

  def setAddressLine4(addressLine4: String)(implicit driver: WebDriver): Unit =
    Browser.find(Browser.name("itmp.address.line4")).foreach(_.underlying.sendKeys(addressLine4))

  def setAddressLine5(addressLine5: String)(implicit driver: WebDriver): Unit =
    Browser.find(Browser.name("itmp.address.line5")).foreach(_.underlying.sendKeys(addressLine5))

  def setPostCode(postcode: String)(implicit driver: WebDriver): Unit =
    Browser.find(Browser.name("itmp.address.postCode")).foreach(_.underlying.sendKeys(postcode))

  def setCountryCode(countryCode: String)(implicit driver: WebDriver): Unit =
    Browser.find(Browser.name("itmp.address.countryCode")).foreach(_.underlying.sendKeys(countryCode))

  def clickSubmit()(implicit driver: WebDriver): Unit =
    Browser.find(Browser.className("button")).foreach(_.underlying.click())

  def enterUserDetails(confidence: Int, credentialStrength: String, userInfo: TestUserInfo)(implicit driver: WebDriver): Unit = {
    navigate()
    setRedirect(EligiblePage.expectedURL)
    setConfidenceLevel(confidence)
    setCredentialStrength(credentialStrength)
    setAddressLines(userInfo.address)
    userInfo.address.postcode.foreach(setPostCode)
    userInfo.address.country.foreach(setCountryCode)
    userInfo.forename.foreach(setGivenName)
    userInfo.surname.foreach(setFamilyName)
    userInfo.nino.foreach(setNino)
    userInfo.dateOfBirth.foreach(d ⇒ setDateOfBirth(d.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))))
    clickSubmit()
  }

  private def setAddressLines(address: Address)(implicit driver: WebDriver): Unit = {
    val setFunctions: List[String ⇒ Unit] = List(
      setAddressLine1 _,
      setAddressLine2 _,
      setAddressLine3 _,
      setAddressLine4 _,
      setAddressLine5 _
    )

      @tailrec
      def loop(acc: List[(String, String ⇒ Unit)]): Unit = acc match {
        case Nil ⇒ ()
        case (line, f) :: tail ⇒
          f(line)
          loop(tail)
      }
    loop(address.lines.zip(setFunctions))
  }

}
