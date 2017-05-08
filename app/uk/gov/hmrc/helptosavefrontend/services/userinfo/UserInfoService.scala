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

package uk.gov.hmrc.helptosavefrontend.services.userinfo

import java.time.LocalDate

import cats.data.{EitherT, ValidatedNel}
import cats.instances.future._
import cats.syntax.cartesian._
import cats.syntax.option._
import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.helptosavefrontend.connectors.CitizenDetailsConnector
import uk.gov.hmrc.helptosavefrontend.connectors.CitizenDetailsConnector.CitizenDetailsResponse
import uk.gov.hmrc.helptosavefrontend.models.UserInfo
import uk.gov.hmrc.helptosavefrontend.services.userinfo.UserInfoService._
import uk.gov.hmrc.helptosavefrontend.util._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}


/**
  * Queries the `user-details` and the `citizen-details` microservices to obtain
  * the required user information. `user-details` is used to obtain name, date of birth,
  * email and `citizen-details` is used to obtain the address.
  *
  */
class UserInfoService(citizenDetailsConnector: CitizenDetailsConnector) extends ServicesConfig {

  def getUserInfo(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[UserInfo] =
    for {
      userDetails ← queryUserDetails()
      citizenDetails ← citizenDetailsConnector.getDetails(nino)
      userInfo ← EitherT.fromEither[Future](toUserInfo(userDetails, citizenDetails, nino).toEither).leftMap(
        errors ⇒ s"Could not create user info: ${errors.toList.mkString(",")}")
    } yield userInfo

  private def queryUserDetails()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[UserDetailsResponse] =
    EitherT.right[Future, String, UserDetailsResponse](
      Future.successful(UserDetailsResponse("test", Some("last"), Some("test@test.com"), Some(LocalDate.now()))))

  private def toUserInfo(u: UserDetailsResponse,
                         c: CitizenDetailsResponse,
                         nino: NINO): ValidatedNel[String, UserInfo] = {
    val surnameValidation =
      u.lastName.orElse(c.person.flatMap(_.lastName))
        .toValidNel("Could not find last name")

    val dateOfBirthValidation =
      u.dateOfBirth.orElse(c.person.flatMap(_.dateOfBirth))
        .toValidNel("Could not find date of birth")

    val emailValidation =
      u.email.toValidNel("Could not find email address")

    val addressValidation = c.address.map(_.toList()).filter(_.nonEmpty)
      .toValidNel("Could not find address")

    (surnameValidation |@| dateOfBirthValidation |@| emailValidation |@| addressValidation)
      .map((surname, dateOfBirth, email, address) ⇒
        UserInfo(u.name + " " + surname, nino, dateOfBirth, email, address)
      )
  }

}

object UserInfoService {

  case class UserDetailsResponse(name: String,
                                 lastName: Option[String],
                                 email: Option[String],
                                 dateOfBirth: Option[LocalDate])

  implicit val userDetailsResponseReads: Reads[UserDetailsResponse] = Json.reads[UserDetailsResponse]


}
