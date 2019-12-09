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
import hts.pages.registrationPages.{CheckDetailsCreateAccountPage, EligiblePage, NotEligibleReason3Page}
import hts.utils.ScenarioContext

import scala.collection.JavaConverters._

class SecuritySteps extends BasePage {

  def oneOfRegex(options: Set[String]): String = s"(${options.mkString("|")})"

  val confidenceLevelRegex: String = oneOfRegex(Set(50, 100, 200, 300).map(_.toString))

  val credentialStrengthsRegex: String = oneOfRegex(Set("weak", "strong", "none"))

  Given("^they have logged into Government Gateway with a confidence level of (.+)") { level: Int ⇒
    AuthorityWizardPage.authenticateUser(EligiblePage.expectedURL, level, "Strong", ScenarioContext.generateEligibleNINO())
  }

  Then("^they are forced into going through IV before being able to proceed with their HtS application$") { () ⇒
    Browser.getCurrentUrl should include regex "/iv/journey-result|iv%2Fjourney-result"
  }

  Given("^the user has logged in and passed IV$") {
    AuthorityWizardPage.authenticateEligibleUser(EligiblePage.expectedURL, ScenarioContext.generateEligibleNINO())
  }

  When("^they try to view their details without having logged in to GG$") {
    EligiblePage.navigate()
  }

  When("^they try to view the create-an-account page$") {
    CheckDetailsCreateAccountPage.navigate()
  }

  Then("^they are prompted to log into GG$") { () ⇒
    Browser.getCurrentUrl should include ("http://localhost:9553/bas-gateway/")
  }

  Given("^if they visit the URIs below they see a valid response$") { URIs: java.util.List[String] ⇒
    URIs.asScala.foreach { uri ⇒
      Browser.navigateTo(uri)
      Browser.checkPageIsLoaded()
      Browser.pageTitle shouldNot include("not found")
      Browser.pageSource shouldNot include("This page can’t be found")
    }
  }

  Given("^they have gone through GG/2SV/identity check but they are NOT eligible for Help to Save$") {
    AuthorityWizardPage.authenticateEligibleUser(EligiblePage.expectedURL, ScenarioContext.generateIneligibleNINO())
  }

  Then("^they still see confirmation that they are NOT eligible$") { () ⇒
    val text = "You’re not eligible for a Help to Save account"
    Browser.isTextOnPage(text) shouldBe Right(Set(text))
    Browser.checkCurrentPageIs(NotEligibleReason3Page)
  }

  Given("^HMRC doesn't currently hold an email address for the user$") {
    AuthorityWizardPage.authenticateUserNoEmail(EligiblePage.expectedURL, 200, "Strong", ScenarioContext.generateEligibleNINO())
    Browser.checkHeader(EligiblePage)
  }

}
