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

import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.ConfidenceLevel.L200
import uk.gov.hmrc.auth.core.Retrievals.{allEnrolments, userDetailsUri}
import uk.gov.hmrc.auth.core._

object HtsAuth {

  val NinoEnrolmentWithConfidence: Enrolment = Enrolment("HMRC-NI").withConfidenceLevel(L200)

  val AuthProvider: AuthProviders = AuthProviders(GovernmentGateway)

  val AuthWithConfidence: Predicate = NinoEnrolmentWithConfidence and AuthProvider

  val UserDetailsUrlWithAllEnrolments: Retrieval[Enrolments] = allEnrolments

}
