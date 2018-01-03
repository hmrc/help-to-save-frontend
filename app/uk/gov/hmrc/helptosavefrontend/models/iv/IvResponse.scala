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

package uk.gov.hmrc.helptosavefrontend.models.iv

import uk.gov.hmrc.http.HttpResponse

sealed trait IvResponse

sealed trait IvSuccessResponse extends IvResponse

object IvSuccessResponse {

  case object Success extends IvSuccessResponse

  case object Incomplete extends IvSuccessResponse

  case object FailedMatching extends IvSuccessResponse

  case object FailedIV extends IvSuccessResponse

  case object InsufficientEvidence extends IvSuccessResponse

  case object LockedOut extends IvSuccessResponse

  case object UserAborted extends IvSuccessResponse

  case object Timeout extends IvSuccessResponse

  case object TechnicalIssue extends IvSuccessResponse

  case object PrecondFailed extends IvSuccessResponse

  def fromString(s: String): Option[IvSuccessResponse] = { // scalastyle:ignore cyclomatic.complexity
    s match {
      case "Success"              ⇒ Some(Success)
      case "Incomplete"           ⇒ Some(Incomplete)
      case "FailedMatching"       ⇒ Some(FailedMatching)
      case "FailedIV"             ⇒ Some(FailedIV)
      case "InsufficientEvidence" ⇒ Some(InsufficientEvidence)
      case "LockedOut"            ⇒ Some(LockedOut)
      case "UserAborted"          ⇒ Some(UserAborted)
      case "Timeout"              ⇒ Some(Timeout)
      case "TechnicalIssue"       ⇒ Some(TechnicalIssue)
      case "PreconditionFailed"   ⇒ Some(PrecondFailed)
      case _                      ⇒ None

    }
  }

}

case class IvUnexpectedResponse(r: HttpResponse) extends IvResponse

case class IvErrorResponse(cause: Exception) extends IvResponse
