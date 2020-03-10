/*
 * Copyright 2020 HM Revenue & Customs
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

import uk.gov.hmrc.helptosavefrontend.models.userinfo.{MissingUserInfos, UserInfo}
import uk.gov.hmrc.helptosavefrontend.util.NINO

abstract class HtsContext {
  val isAuthorised: Boolean
}

abstract class HtsContextWithNINO extends HtsContext {
  val nino: NINO
}

abstract class HtsContextWithNINOAndFirstName extends HtsContextWithNINO {
  val firstName: Option[String]
}

abstract class HtsContextWithNINOAndUserDetails extends HtsContextWithNINO {
  val userDetails: Either[MissingUserInfos, UserInfo]
}

object HtsContext {

  def apply(authorised: Boolean): HtsContext = new HtsContext { val isAuthorised: Boolean = authorised }

}

object HtsContextWithNINO {
  def apply(authorised: Boolean, NINO: NINO): HtsContextWithNINO =
    new HtsContextWithNINO {
      override val nino: NINO = NINO
      override val isAuthorised: Boolean = authorised
    }
}

object HtsContextWithNINOAndFirstName {
  def apply(authorised: Boolean, NINO: NINO, maybeName: Option[String]): HtsContextWithNINOAndFirstName =
    new HtsContextWithNINOAndFirstName {
      override val firstName: Option[String] = maybeName
      override val nino: NINO = NINO
      override val isAuthorised: Boolean = authorised
    }
}

object HtsContextWithNINOAndUserDetails {
  def apply(
    authorised: Boolean,
    NINO: NINO,
    details: Either[MissingUserInfos, UserInfo]
  ): HtsContextWithNINOAndUserDetails =
    new HtsContextWithNINOAndUserDetails {
      override val nino: NINO = NINO
      override val isAuthorised: Boolean = authorised
      override val userDetails: Either[MissingUserInfos, UserInfo] = details
    }
}
