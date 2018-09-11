/*
 * Copyright 2018 HM Revenue & Customs
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
import hts.pages._
import hts.pages.accountHomePages.{AccountHolderEmailVerifiedPage, ChangeEmailPage}
import hts.utils.ScenarioContext
import play.api.Configuration
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, CryptoImpl, EmailVerificationParams}

class EmailSteps extends Steps {

  lazy implicit val crypto: Crypto = new CryptoImpl(Configuration(ConfigFactory.defaultApplication()))

  And("^they select their GG email and proceed$") {
    Browser.checkCurrentPageIs(SelectEmailPage)
    SelectEmailPage.selectGGEmail()

    Browser.checkCurrentPageIs(BankDetailsPage)
    BankDetailsPage.enterValidDetails()

    Browser.checkCurrentPageIs(CheckYourDetailsPage)
    CheckYourDetailsPage.continue()
  }

  When("^they start to create an account$") {
    EligiblePage.continue()
  }

  Then("^they are asked to check their email for a verification email$") {
    Browser.checkCurrentPageIs(VerifyYourEmailPage)
  }

  Given("^they've chosen to enter a new email address during the application process$") {
    AuthorityWizardPage.authenticateEligibleUser(EligiblePage.expectedURL, ScenarioContext.currentNINO())
    EligiblePage.continue()
    SelectEmailPage.setAndVerifyNewEmail("newemail@mail.com")
  }

  When("^they click on the email verification link$"){
    val params = EmailVerificationParams(ScenarioContext.currentNINO(), "newemail@mail.com")
    Browser.navigateTo(s"email-verified-callback?p=${params.encode()}")
  }

  Then("^they see that their email has been successfully verified$"){
    Browser.checkCurrentPageIs(ApplicantEmailVerifiedPage)
    ApplicantEmailVerifiedPage.checkForOldQuotes()
  }

  When("^they request a re-send of the verification email$") {
    VerifyYourEmailPage.resendVerificationEmail()
  }

  When("^they want to change their email again$") {
    VerifyYourEmailPage.changeEmail()
    SelectEmailPage.setAndVerifyNewEmail("secondnewemail@mail.com")
  }

  Then("^they are asked to enter an email address$") {
    Browser.checkCurrentPageIs(EnterEmailPage)
    EnterEmailPage.checkForOldQuotes()
  }

  Then("^they see the final Create Account page$") {
    Browser.checkCurrentPageIs(CreateAccountPage)
    CreateAccountPage.checkForOldQuotes()
  }

  Given("^the account holder has chosen to enter a new email address$"){ () ⇒
    AuthorityWizardPage.authenticateEligibleUser(EligiblePage.expectedURL, ScenarioContext.generateEligibleNINO())
    createAccountUsingGGEmail()

    ChangeEmailPage.navigate()
    ChangeEmailPage.setNewEmailAddress("anotheremail@mail.com")
  }

  When("^the account holder clicks on the email verification link$"){ () ⇒
    val params = EmailVerificationParams(ScenarioContext.currentNINO(), "anotheremail@mail.com")
    Browser.navigateTo(s"account-home/email-verified-callback?p=${params.encode()}")
  }

  Then("^the account holder sees that their email has been successfully verified$"){ () ⇒
    Browser.checkCurrentPageIs(AccountHolderEmailVerifiedPage)
    AccountHolderEmailVerifiedPage.checkForOldQuotes()
  }

}
