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

package hts.pages.identityPages

import hts.browser.Browser
import hts.pages.Page
import org.openqa.selenium.WebDriver

object IdentityVerificationStubPage extends Page {

  override val expectedURL: String = "http://localhost:9948/mdtp/uplift?origin=help-to-save-frontend&completionURL=http%3A%2F%2Flocalhost%3A7000%2Fhelp-to-save%2Fiv%2Fjourney-result%3FcontinueURL%3D%2Fhelp-to-save%2Fcheck-eligibility&failureURL=http%3A%2F%2Flocalhost%3A7000%2Fhelp-to-save%2Fiv%2Fjourney-result%3FcontinueURL%3D%2Fhelp-to-save%2Fcheck-eligibility&confidenceLevel=200"

  def selectSuccessfulJourney()(implicit driver: WebDriver): Unit = Browser.click.on("requiredResult-success")

  def selectJourney(reason: String)(implicit driver: WebDriver): Unit = {
    reason match {

      /*
      There is a number of ways to select the radio buttons:
      Browser.click on "{radioButtonID}"
      Browser.click on Browser.radioButton("{value}")
      Browser.radioButtonGroup("{radioButtonSharedName}").value="{value}"
      Browser.radioButtonGroup("{radioButtonSharedName}").selection=Some("{value}")
      etc.
       */

//      case "Success"               ⇒ Browser.radioButtonGroup("requiredResult").value = "Success"
      case "Failed IV"             ⇒ Browser.radioButtonGroup("requiredResult").value = "Incomplete"
      case "Precondition Failed"   ⇒ Browser.radioButtonGroup("requiredResult").value = "PreconditionFailed"
      case "Locked Out"            ⇒ Browser.radioButtonGroup("requiredResult").value = "LockedOut"
      case "Insufficient Evidence" ⇒ Browser.radioButtonGroup("requiredResult").value = "InsufficientEvidence"
      case "Failed Matching"       ⇒ Browser.radioButtonGroup("requiredResult").value = "FailedMatching"
      case "Technical Issue"       ⇒ Browser.radioButtonGroup("requiredResult").value = "TechnicalIssue"
      case "User Aborted"          ⇒ Browser.radioButtonGroup("requiredResult").value = "UserAborted"
      case "Timed Out"             ⇒ Browser.radioButtonGroup("requiredResult").value = "Timeout"
      case _                       ⇒ println("Invalid Journey - please input the exact name of the selected journey as is displayed on the Stub page")

    }
  }

  def submitJourney()(implicit driver: WebDriver): Unit = Browser.submit()

  def checkIVResultPage(reason: String)(implicit driver: WebDriver): Unit = {
    reason match {
//      case "Success" ⇒ Browser.checkCurrentPageIs(IdentityVerifiedPage)
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
      case _                       ⇒ println("Invalid Journey - please input the exact name of the selected journey as is displayed on the Stub page")
    }
  }

  def executeIVResultPageAction(reason: String)(implicit driver: WebDriver): Unit = {
    reason match {
//      case "Success"               ⇒ IdentityVerifiedPage.continue()
      case "Failed IV"             ⇒ FailedIVPage.tryAgain()
      case "Precondition Failed"   ⇒ FailedIVPreconditionFailedPage.clickExitLink()
      case "Locked Out"            ⇒ FailedIVLockedOutPage.clickExitLink()
      case "Insufficient Evidence" ⇒ FailedIVInsufficientEvidencePage.checkTelephone()
      case "Failed Matching"       ⇒ FailedIVMatchingPage.tryAgain()
      case "Technical Issue"       ⇒ FailedIVTechnicalIssuePage.tryAgain()
      case "User Aborted"          ⇒ FailedIVUserAbortedPage.tryAgain()
      case "Timed Out"             ⇒ FailedIVTimeOutPage.tryAgain()
      case _                       ⇒ println("Invalid Journey - please input the exact name of the selected journey as is displayed on the Stub page")
    }
  }

}
