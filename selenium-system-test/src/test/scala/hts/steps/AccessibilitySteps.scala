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
import hts.pages.ErrorPages.{LinkExpiredPage, NoAccountPage}
import hts.pages.registrationPages.CreateAccountErrorPage

class AccessibilitySteps extends Steps {

  When("^a user views the create account error page$"){
    CreateAccountErrorPage.navigate()
    Browser.checkCurrentPageIs(CreateAccountErrorPage)
  }

  When("^a user views the link expired page$"){
    LinkExpiredPage.navigate()
    Browser.checkCurrentPageIs(LinkExpiredPage)
  }

  When("^a user views the no account page$"){
    NoAccountPage.navigate()
    Browser.checkCurrentPageIs(NoAccountPage)
  }

  Then("^they see that the create account error page has only smart quotes$"){
    CreateAccountErrorPage.checkForOldQuotes()
  }

  Then("^they see that the link expired page has only smart quotes$"){
    LinkExpiredPage.checkForOldQuotes()
  }

  Then("^they see that the no account page has only smart quotes$"){
    NoAccountPage.checkForOldQuotes()
  }
}
