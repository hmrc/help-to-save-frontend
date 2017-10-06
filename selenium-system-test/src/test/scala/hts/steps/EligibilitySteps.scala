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

import hts.pages.registrationPages.CheckEligibilityPage
import hts.pages.{AuthorityWizardPage, EligiblePage, NotEligiblePage}
import hts.utils.{Configuration, ScenarioContext}

class EligibilitySteps extends Steps {

  Given("""^an user is in receipt of working tax credit$""") { () ⇒
    val _ = ScenarioContext.generateEligibleNINO()
  }

  When("""^they apply for Help to Save$""") { () ⇒
    AuthorityWizardPage.authenticateUser(CheckEligibilityPage.url, 200, "Strong", ScenarioContext.currentNINO)
  }

  Then("""^they see that they are eligible for Help to Save$""") { () ⇒
    EligiblePage.pageInfoIsCorrect
  }

  When("""^they start to create an account$"""){ () ⇒
    EligiblePage.startCreatingAccount()
  }

  Given("""^an user is NOT in receipt of working tax credit$""") { () ⇒
    val _ = ScenarioContext.generateIneligibleNINO()
  }

  Then("""^they see that they are NOT eligible for Help to Save$""") { () ⇒
    NotEligiblePage.pageInfoIsCorrect

  }
}
