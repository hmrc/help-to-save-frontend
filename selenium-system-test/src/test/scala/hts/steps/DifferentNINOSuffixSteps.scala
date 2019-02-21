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

package hts.steps

import com.typesafe.config.ConfigFactory
import hts.browser.Browser
import hts.pages.AuthorityWizardPage
import hts.pages.accountHomePages.{AccessAccountLink, ChangeEmailPage}
import hts.pages.registrationPages.EligiblePage
import hts.utils.ScenarioContext
import play.api.Configuration
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, CryptoImpl, EmailVerificationParams}

class DifferentNINOSuffixSteps extends Steps {

  lazy implicit val crypto: Crypto = new CryptoImpl(Configuration(ConfigFactory.defaultApplication()))

  val ninoForSuffixTest = ScenarioContext.generateEligibleNINO()
  val ninoWithSuffixC = ninoForSuffixTest.take(8).toString + "C"
  val ninoWithSuffixD = ninoWithSuffixC.take(8).toString + "D"
  val newEmail = "newemail@mail.com"

  Given("^the account holder has enrolled with NINO suffix C$") { () ⇒
    AuthorityWizardPage.authenticateEligibleUser(EligiblePage.expectedURL, ninoWithSuffixC)
    createAccountUsingGGEmail()
  }

  When("^the account holder logs in with suffix D$") { () ⇒
    AuthorityWizardPage.authenticateEligibleUser(AccessAccountLink.expectedURL, ninoWithSuffixD)
  }

  And("^successfully updates their email address$") { () ⇒
    ChangeEmailPage.navigate()
    ChangeEmailPage.setNewEmailAddress(newEmail)
    val params = EmailVerificationParams(ninoWithSuffixD, newEmail)
    Browser.navigateTo(s"account-home/email-confirmed-callback?p=${params.encode()}")
  }

  // use these if we connect test to NSI
  When("^they revisit their account with suffix C$") { () ⇒
    AuthorityWizardPage.authenticateEligibleUser(AccessAccountLink.expectedURL, ninoWithSuffixC)
  }

  Then("^the updated email will be visible$") { () ⇒
    ChangeEmailPage.navigate()
    Browser.getPageContent should contain(newEmail)
  }

}
