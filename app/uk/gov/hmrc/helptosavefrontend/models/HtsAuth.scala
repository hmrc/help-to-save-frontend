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

package uk.gov.hmrc.helptosavefrontend.models

import org.joda.time.LocalDate
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Name ⇒ CoreName, _}
import uk.gov.hmrc.auth.core.{AuthProviders, ConfidenceLevel}

object HtsAuth {

  val AuthProvider: AuthProviders = AuthProviders(GovernmentGateway)

  val AuthWithCL200: Predicate = AuthProvider and ConfidenceLevel.L200

  val UserInfoRetrievals: Retrieval[Option[CoreName] ~ Option[String] ~ Option[LocalDate] ~ Option[ItmpName] ~ Option[LocalDate] ~ Option[ItmpAddress]] =
    v2.Retrievals.name and
      v2.Retrievals.email and
      v2.Retrievals.dateOfBirth and
      v2.Retrievals.itmpName and
      v2.Retrievals.itmpDateOfBirth and
      v2.Retrievals.itmpAddress
}
