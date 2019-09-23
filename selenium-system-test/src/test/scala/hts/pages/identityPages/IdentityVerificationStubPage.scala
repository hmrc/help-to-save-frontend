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

package hts.pages.identityPages

import hts.browser.Browser
import hts.pages.BasePage
import org.openqa.selenium.WebDriver

object IdentityVerificationStubPage extends BasePage {

  override val expectedURL: String = "http://localhost:9948/mdtp/uplift?origin=help-to-save-frontend&completionURL=http%3A%2F%2Flocalhost%3A7000%2Fhelp-to-save%2Fiv%2Fjourney-result%3FcontinueURL%3D%2Fhelp-to-save%2Fcheck-eligibility&failureURL=http%3A%2F%2Flocalhost%3A7000%2Fhelp-to-save%2Fiv%2Fjourney-result%3FcontinueURL%3D%2Fhelp-to-save%2Fcheck-eligibility&confidenceLevel=200"

  def selectSuccessfulJourney()(implicit driver: WebDriver): Unit = {
    Browser.click.on("requiredResult-success")
    Browser.submit()
  }

  private def selectRadioButton(reasonLabel: String)(implicit driver: WebDriver): Unit = {
    Browser.radioButtonGroup("requiredResult").value = reasonLabel
  }

  def selectJourney(reason: String)(implicit driver: WebDriver): Unit = {
    reason match {
      case "Failed IV"             ⇒ selectRadioButton("FailedIV")
      case "Precondition Failed"   ⇒ selectRadioButton("PreconditionFailed")
      case "Locked Out"            ⇒ selectRadioButton("LockedOut")
      case "Insufficient Evidence" ⇒ selectRadioButton("InsufficientEvidence")
      case "Failed Matching"       ⇒ selectRadioButton("FailedMatching")
      case "Technical Issue"       ⇒ selectRadioButton("TechnicalIssue")
      case "User Aborted"          ⇒ selectRadioButton("UserAborted")
      case "Timed Out"             ⇒ selectRadioButton("Timeout")
      case _                       ⇒ sys.error("Invalid Journey - please input the exact name of the selected journey as is displayed on the Stub page")
    }
    Browser.submit()

    if (reason == "Failed IV") {
      FailedIVPage.navigate()
    }
  }

}
