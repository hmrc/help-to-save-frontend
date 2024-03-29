/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.helptosavefrontend.models.userinfo

import cats.Show
import play.api.libs.json._
import uk.gov.hmrc.helptosavefrontend.util.Email

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Details of the user obtained from HMRC services */
case class UserInfo(
  forename: String,
  surname: String,
  nino: String,
  dateOfBirth: LocalDate,
  email: Option[String],
  address: Address
)

object UserInfo {

  implicit val userDetailsFormat: Format[UserInfo] = Json.format[UserInfo]

  implicit val localDateShow: Show[LocalDate] =
    Show.show(date => date.format(DateTimeFormatter.ofPattern("d MMMM yyyy")))

  implicit class UserInfoOps(val u: UserInfo) extends AnyVal {

    def updateEmail(email: Email): UserInfo = u.copy(email = Some(email))

  }

}
