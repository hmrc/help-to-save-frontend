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

import org.joda.time.LocalDate
import uk.gov.hmrc.auth.core.authorise.ConfidenceLevel.L200
import uk.gov.hmrc.auth.core.authorise.{Enrolment, Predicate}
import uk.gov.hmrc.auth.core.retrieve.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.retrieve.{AuthProviders, ItmpAddress, ItmpName, Name, Retrieval, Retrievals, ~}

object HtsAuth {

  val NinoWithCL200: Enrolment = Enrolment("HMRC-NI").withConfidenceLevel(L200)

  val AuthProvider: AuthProviders = AuthProviders(GovernmentGateway)

  val AuthWithCL200: Predicate = NinoWithCL200 and AuthProvider

  val UserRetrievals: Retrieval[Name ~ Option[String] ~ Option[LocalDate] ~ ItmpName ~ Option[LocalDate] ~ ItmpAddress] =
    Retrievals.name and
      Retrievals.email and
      Retrievals.dateOfBirth and
      Retrievals.itmpName and
      Retrievals.itmpDateOfBirth and
      Retrievals.itmpAddress
}
