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

package hts.pages.identityPages

import hts.browser.Browser
import hts.pages.Page
import org.openqa.selenium.WebDriver

object IdentityVerificationStubPage extends Page {

  override val expectedURL: String = "http://localhost:9948/mdtp/uplift?origin=help-to-save-frontend&completionURL=http%3A%2F%2Flocalhost%3A7000%2Fhelp-to-save%2Fiv%2Fjourney-result%3FcontinueURL%3D%2Fhelp-to-save%2Fcheck-eligibility&failureURL=http%3A%2F%2Flocalhost%3A7000%2Fhelp-to-save%2Fiv%2Fjourney-result%3FcontinueURL%3D%2Fhelp-to-save%2Fcheck-eligibility&confidenceLevel=200"

  def selectSuccessfulJourney()(implicit driver: WebDriver): Unit = Browser.click.on("requiredResult-success")

  private def selectRadioButton(reasonLabel: String)(implicit driver: WebDriver): Unit ={
    Browser.radioButtonGroup("requiredResult").value = reasonLabel
  }

  def selectJourney(reason: String)(implicit driver: WebDriver): Unit = {
    reason match {
      case "Failed IV"             ⇒ selectRadioButton("Incomplete")
      case "Precondition Failed"   ⇒ selectRadioButton("PreconditionFailed")
      case "Locked Out"            ⇒ selectRadioButton("LockedOut")
      case "Insufficient Evidence" ⇒ selectRadioButton("InsufficientEvidence")
      case "Failed Matching"       ⇒ selectRadioButton("FailedMatching")
      case "Technical Issue"       ⇒ selectRadioButton("TechnicalIssue")
      case "User Aborted"          ⇒ selectRadioButton("UserAborted")
      case "Timed Out"             ⇒ selectRadioButton("Timeout")
      case _                       ⇒ sys.error("Invalid Journey - please input the exact name of the selected journey as is displayed on the Stub page")
    }
  }

  def checkIVResultPage(reason: String)(implicit driver: WebDriver): Unit = {
    reason match {
      case "Failed IV" ⇒
        Browser.navigateTo("failed-iv")
        Browser.checkCurrentPageIs(FailedIVPage)
      case "Precondition Failed"   ⇒ Browser.checkCurrentPageIs(FailedIVPreconditionFailedPage)
      case "Locked Out"            ⇒ Browser.checkCurrentPageIs(FailedIVLockedOutPage)
      case "Insufficient Evidence" ⇒ Browser.checkCurrentPageIs(FailedIVInsufficientEvidencePage)
      case "Failed Matching"       ⇒ Browser.checkCurrentPageIs(FailedIVMatchingPage)
      case "Technical Issue"       ⇒ Browser.checkCurrentPageIs(FailedIVTechnicalIssuePage)
      case "User Aborted"          ⇒ Browser.checkCurrentPageIs(FailedIVUserAbortedPage)
      case "Timed Out"             ⇒ Browser.checkCurrentPageIs(FailedIVTimeOutPage)
      case _                       ⇒ sys.error("Invalid Journey - please input the exact name of the selected journey as is displayed on the Stub page")
    }
  }

  def executeIVResultPageAction(reason: String)(implicit driver: WebDriver): Unit = {
    reason match {
      case "Failed IV"             ⇒ FailedIVPage.tryAgain()
      case "Precondition Failed"   ⇒ FailedIVPreconditionFailedPage.clickExitLink()
      case "Locked Out"            ⇒ FailedIVLockedOutPage.clickExitLink()
      case "Insufficient Evidence" ⇒ FailedIVInsufficientEvidencePage.checkTelephone()
      case "Failed Matching"       ⇒ FailedIVMatchingPage.tryAgain()
      case "Technical Issue"       ⇒ FailedIVTechnicalIssuePage.tryAgain()
      case "User Aborted"          ⇒ FailedIVUserAbortedPage.tryAgain()
      case "Timed Out"             ⇒ FailedIVTimeOutPage.tryAgain()
      case _                       ⇒ sys.error("Invalid Journey - please input the exact name of the selected journey as is displayed on the Stub page")
    }
  }

  def submitJourney()(implicit driver: WebDriver): Unit = Browser.submit()

}
