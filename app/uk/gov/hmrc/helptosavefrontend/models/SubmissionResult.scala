/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.helptosavefrontend.models

import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.helptosavefrontend.models.account.AccountNumber

sealed trait SubmissionResult

object SubmissionResult {
  case class SubmissionFailure(errorMessageId: Option[String], errorMessage: String, errorDetail: String)
      extends SubmissionResult

  object SubmissionFailure {
    implicit val submissionFailureFormat: Format[SubmissionFailure] = Json.format[SubmissionFailure]
  }
  case class SubmissionSuccess(accountNumber: AccountNumber) extends SubmissionResult

  object SubmissionSuccess {
    implicit val submissionSuccessFormat: Format[SubmissionSuccess] = Json.format[SubmissionSuccess]
  }
}
