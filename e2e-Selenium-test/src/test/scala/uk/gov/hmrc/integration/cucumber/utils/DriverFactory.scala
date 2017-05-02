/*
 * Copyright (c) 2014 ContinuumSecurity www.continuumsecurity.net
 *
 * The contents of this file are subject to the GNU Affero General Public
 * License version 3 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * The Initial Developer of the Original Code is ContinuumSecurity.
 * Portions created by ContinuumSecurity are Copyright (C)
 * ContinuumSecurity SLNE. All Rights Reserved.
 *
 * Contributor(s): Stephen de Vries
 */
package uk.gov.hmrc.integration.cucumber.utils

import org.openqa.selenium.Proxy
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxProfile
import org.openqa.selenium.firefox.internal.ProfilesIni
import org.openqa.selenium.remote.CapabilityType
import org.openqa.selenium.remote.DesiredCapabilities
import java.io.File



object DriverFactory {

  private val CHROME: String = "chrome"
  private val FIREFOX: String = "firefox"

  def createProxyDriver(`type`: String, proxy: Proxy, path: String): WebDriver = {
    if (`type`.equalsIgnoreCase(CHROME)) return createChromeDriver(createProxyCapabilities(proxy), path)
    else if (`type`.equalsIgnoreCase(FIREFOX)) return createFirefoxDriver(createProxyCapabilities(proxy))
    throw new RuntimeException("Unknown WebDriver browser: " + `type`)
  }

  def createChromeDriver(capabilities: DesiredCapabilities, path: String): WebDriver = {
    System.setProperty("webdriver.chrome.driver", path)
    if (capabilities != null) {
      capabilities.setCapability(CapabilityType.ACCEPT_SSL_CERTS, true)
      return new ChromeDriver(capabilities)
    }
    else return new ChromeDriver
  }

  def createFirefoxDriver(capabilities: DesiredCapabilities): WebDriver = {
    if (capabilities != null) {
      return new FirefoxDriver(capabilities)
    }
    val allProfiles: ProfilesIni = new ProfilesIni
    var myProfile: FirefoxProfile = allProfiles.getProfile("WebDriver")
    if (myProfile == null) {
      val ffDir: File = new File(System.getProperty("user.dir") + File.separator + "ffProfile")
      if (!ffDir.exists) {
        ffDir.mkdir
      }
      myProfile = new FirefoxProfile(ffDir)
    }
    myProfile.setAcceptUntrustedCertificates(true)
    myProfile.setAssumeUntrustedCertificateIssuer(true)
    myProfile.setPreference("webdriver.load.strategy", "unstable")
    if (capabilities == null) {
      val capabilities = new DesiredCapabilities
    }
    capabilities.setCapability(FirefoxDriver.PROFILE, myProfile)
    return new FirefoxDriver(capabilities)
  }

  def createProxyCapabilities(proxy: Proxy): DesiredCapabilities = {
    val capabilities: DesiredCapabilities = DesiredCapabilities.chrome
    capabilities.setCapability("proxy", proxy)
    return capabilities
  }
}