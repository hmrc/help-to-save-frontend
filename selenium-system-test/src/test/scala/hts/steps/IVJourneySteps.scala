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

import hts.browser.Browser
import hts.pages._
import hts.pages.identityPages._
import hts.pages.registrationPages.{CheckEligibilityLink, EligiblePage}
import hts.utils.ScenarioContext

class IVJourneySteps extends BasePage {

  Given("^an applicant who hasn't been through IV applies$") {
    AuthorityWizardPage.authenticateIneligibleUser(CheckEligibilityLink.expectedURL, ScenarioContext.generateEligibleNINO())
  }

  When("^they successfully go through the IV journey$") {
    IdentityVerificationStubPage.selectSuccessfulJourney()

    Browser.checkCurrentPageIs(IdentityVerifiedPage)
    IdentityVerifiedPage.checkForOldQuotes()
    IdentityVerifiedPage.continue()
  }

  Then("^they see that they have passed the eligibility check$") {
    Browser.checkHeader(EligiblePage)
  }

  When("^they go through the IV journey and fail because of (.+)$") { reason: String ⇒
    Browser.checkCurrentPageIs(IdentityVerificationStubPage)
    IdentityVerificationStubPage.selectJourney(reason)
  }

  Then("^they will see the (.+)$") { reason: String ⇒
    val reasonPage = IVPage.fromString(reason)
    Browser.checkCurrentPageIs(reasonPage)
    reasonPage.executeIVResultPageAction()
  }

  Then("^they will see generic HTS contact page$") {
    IdentityVerificationStubPage.checkforHTSGenericPage()
  }

}
