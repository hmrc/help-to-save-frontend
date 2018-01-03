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
import hts.pages.identityPages._
import hts.pages.registrationPages._
import hts.utils.ScenarioContext

class IVJourneySteps extends Steps {

  Given("^an applicant who hasn't been through IV applies$") {
    AuthorityWizardPage.authenticateUser(ApplyPage.expectedURL, 50, "Strong", ScenarioContext.generateEligibleNINO())
    ApplyPage.navigate()

    Browser.checkCurrentPageIs(ApplyPage)
    ApplyPage.clickStartNow()
  }

  When("^they go through IV check successfully and continue$") {
    IdentityVerificationStubPage.selectSuccessfulJourney()

    Browser.checkCurrentPageIs(IdentityVerifiedPage)
    IdentityVerifiedPage.continue()
  }

  Then("^they will be redirected to the eligibility check and pass it$") {
    Browser.checkCurrentPageIs(EligiblePage)

    AboutPage.navigate()
    Browser.checkCurrentPageIs(AboutPage)
  }

  When("^they go through IV and fail because of (.+)") { reason: String ⇒
    Browser.checkCurrentPageIs(IdentityVerificationStubPage)
    IdentityVerificationStubPage.selectJourney(reason)
  }

  Then("^they will see the (.+) page$") { reason: String ⇒
    val reasonPage = IVPage.fromString(reason)
    Browser.checkCurrentPageIs(reasonPage)
    reasonPage.executeIVResultPageAction()
  }

}
