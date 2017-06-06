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

import hts.pages.{CreateAccountPage, ConfirmDetailsPage, Page}

class CreateAccountSteps extends Steps{

  When("""^they choose to create an account$""") { () =>
    ConfirmDetailsPage.continue()
    CreateAccountPage.createAccount()
  }

  Then("""^they see that the account is created$""") { () =>
    Page.getPageContent() should include("Successfully created account")
  }

}
