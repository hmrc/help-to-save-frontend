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

import hts.browser.Browser
import hts.pages._
import hts.pages.accountHomePages.{ChangeEmailPage, VerifyEmailPage}
import hts.pages.registrationPages._
import hts.utils.ScenarioContext

class AccessibilitySteps extends Steps {

  Then("^they go through the happy path$") {
    AboutPage.navigate()
    Browser.checkCurrentPageIs(AboutPage)
    checkPrivacyPolicyPage(AboutPage)
    Browser.nextPage()

    Browser.checkCurrentPageIs(EligibilityInfoPage)
    Browser.nextPage()

    Browser.checkCurrentPageIs(HowTheAccountWorksPage)
    Browser.nextPage()

    Browser.checkCurrentPageIs(HowWeCalculateBonusesPage)
    verifyGovUKLink()
    Browser.nextPage()

    Browser.checkCurrentPageIs(ApplyPage)
    ApplyPage.clickStartNow()

    Browser.checkCurrentPageIs(EligiblePage)
    EligiblePage.clickConfirmAndContinue()

    SelectEmailPage.setAndVerifyNewEmail("newemail@mail.com")
    Browser.checkCurrentPageIs(VerifyYourEmailPage)

    Browser.goBack()
    SelectEmailPage.selectGGEmail()
    CreateAccountPage.createAccount()

    Browser.checkCurrentPageIs(NsiManageAccountPage)

    ChangeEmailPage.navigate()
    Browser.checkCurrentPageIs(ChangeEmailPage)
    ChangeEmailPage.setNewEmailAddress("anotheremail@mail.com")

    Browser.checkCurrentPageIs(VerifyEmailPage)
    VerifyEmailPage.resendEmail

    Browser.checkCurrentPageIs(VerifyEmailPage)
    VerifyEmailPage.goToAccountHome

    Browser.checkCurrentPageIs(NsiManageAccountPage)
  }

  private def checkPrivacyPolicyPage(currentPage: Page): Unit = {
    Browser.openAndCheckPageInNewWindowUsingLinkText("Privacy policy", PrivacyPolicyPage)

    currentPage.navigate()
    Browser.checkCurrentPageIs(currentPage)
  }

  private def verifyGovUKLink(): Unit = {
    Browser.clickButtonByIdOnceClickable("logo")
    Browser.checkCurrentPageIs(GovUKPage)
    Browser.goBack()
  }

}
