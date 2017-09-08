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

import hts.pages.{AuthorityWizardPage, ConfirmDetailsPage, EligiblePage}
import hts.utils.{Configuration, NINOGenerator}

class EmailSteps extends Steps with NINOGenerator {

  Given("""^an applicant is viewing their applicant details$"""){ () ⇒
    AuthorityWizardPage.authenticateUser(s"${Configuration.host}/help-to-save/check-eligibility", 200, "Strong", currentEligibleNINO)
    EligiblePage.startCreatingAccount()
  }

  When("""^they choose to change their email address$"""){ () ⇒
    ConfirmDetailsPage.changeEmail()

  }

  Then("""^they are asked to check their email account for a verification email$"""){ () ⇒

  }

}
