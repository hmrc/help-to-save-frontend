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

package src.test.scala.hts.steps

import src.test.scala.hts.pages.registrationPages._
import src.test.scala.hts.pages.{ConfirmDetailsPage, CreateAccountPage, Page}

class CreateAccountSteps extends Steps{

  Given("""^A user is at the start of the registration process$"""){ () =>
    AboutPage.navigateToAboutPage()
  }

  When("""^they proceed through to the apply page$"""){ () =>
    AboutPage.nextPage()
    EligibilityPage.nextPage()
    HowTheAccountWorksPage.nextPage()
    HowWeCalculateBonusesPage.nextPage()
  }

  When("""^they click on the Start now button$"""){ () =>
    ApplyPage.clickStartNow()
  }

  When("""^they choose to not create an account$"""){ () =>
    ConfirmDetailsPage.continue()
    CreateAccountPage.exitWithoutCreatingAccount()
  }

  When("""^they choose to create an account$""") { () =>
    ConfirmDetailsPage.continue()
    CreateAccountPage.createAccount()
  }

  Then("""^they see that the account is created$""") { () =>
    Page.getPageContent() should include("Successfully created account")
  }

  Then("""^they see the gov uk page$"""){ () =>
    getDriverUnsafe.getCurrentUrl shouldBe "https://www.gov.uk/"
  }


}
