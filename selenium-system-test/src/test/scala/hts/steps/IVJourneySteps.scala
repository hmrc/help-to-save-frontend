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

package hts.steps

import hts.browser.Browser
import hts.pages._
import hts.pages.identityPages._
import hts.pages.registrationPages._
import hts.utils.ScenarioContext

class IVJourneySteps extends Steps {

  //  TO RUN, USE: ./run_selenium_system_test.sh local chrome /usr/local/bin/chromedriver wip

  Given("^an applicant who hasn't been through identity verification is on the Apply page$"){
    AuthorityWizardPage.authenticateUser(ApplyPage.expectedURL, 50, "Strong", ScenarioContext.generateEligibleNINO())
    ApplyPage.navigate()
  }

  When("^they go through identity verification check successfully and continue$"){
    Browser.checkCurrentPageIs(ApplyPage)
    ApplyPage.clickStartNow()

    IdentityVerificationStubPage.selectSuccessfulJourney()
    IdentityVerificationStubPage.submitJourney()

    Browser.checkCurrentPageIs(IdentityVerifiedPage)
    IdentityVerifiedPage.continue()
  }

  Then("^they will be redirected to the eligibility check and pass it$"){
    Browser.checkCurrentPageIs(EligiblePage)

    AboutPage.navigate()
    Browser.checkCurrentPageIs(AboutPage)
  }

  When("^they go through identity verification and fail because of (.+)"){ (reason: String) ⇒
    Browser.checkCurrentPageIs(ApplyPage)
    ApplyPage.clickStartNow()

    IdentityVerificationStubPage.selectJourney(reason)
    IdentityVerificationStubPage.submitJourney()
  }

  Then("^they will see the (.+) page$"){ (reason: String) ⇒
    IdentityVerificationStubPage.checkIVResultPage(reason)
    IdentityVerificationStubPage.executeIVResultPageAction(reason)
  }

}
