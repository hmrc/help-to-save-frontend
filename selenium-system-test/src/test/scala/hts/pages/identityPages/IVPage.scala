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

import hts.pages.Page
import org.openqa.selenium.WebDriver

object IVPage {

  def fromString(reason: String): IVPage =
    reason match {
      case "Failed IV"             ⇒ FailedIVPage
      case "Precondition Failed"   ⇒ FailedIVPreconditionFailedPage
      case "Locked Out"            ⇒ FailedIVLockedOutPage
      case "Insufficient Evidence" ⇒ FailedIVInsufficientEvidencePage
      case "Failed Matching"       ⇒ FailedIVMatchingPage
      case "Technical Issue"       ⇒ FailedIVTechnicalIssuePage
      case "User Aborted"          ⇒ FailedIVUserAbortedPage
      case "Timed Out"             ⇒ FailedIVTimeOutPage
      case _                       ⇒ sys.error(s"Unknown IV reason: $reason; Please use the reason name exactly as stated on the IV Stub page")
    }

}

trait IVPage extends Page {

  def executeIVResultPageAction()(implicit driver: WebDriver): Unit

}
