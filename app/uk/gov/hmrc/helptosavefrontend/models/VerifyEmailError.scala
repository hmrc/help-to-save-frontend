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

package uk.gov.hmrc.helptosavefrontend.models

import uk.gov.hmrc.helptosavefrontend.util._

sealed trait VerifyEmailError

object VerifyEmailError {

  case class RequestNotValidError(nino: NINO) extends VerifyEmailError

  case class VerificationServiceUnavailable() extends VerifyEmailError

  case class AlreadyVerified(nino: NINO, emailAddress: String) extends VerifyEmailError

  case class BackendError(message: String) extends VerifyEmailError

}
