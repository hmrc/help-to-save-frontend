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

import cats.data.EitherT
import cats.instances.future._
import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.helptosavefrontend.connectors.CitizenDetailsConnector
import uk.gov.hmrc.helptosavefrontend.connectors.CitizenDetailsConnector.CitizenDetailsResponse
import uk.gov.hmrc.helptosavefrontend.models.UserInfo
import uk.gov.hmrc.helptosavefrontend.services.userinfo.UserInfoService._
import uk.gov.hmrc.helptosavefrontend.util._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}


/**
  * Queries the `user-details` and the `citizen-details` microservices to obtain
  * the required user information. `user-details` is used to obtain name, date of birth,
  * email and `citizen-details` is used to obtain the address.
  *
  * The input to this service requires an [[AuthContext]] and a [[NINO]].
  *
  * @param authConnector The [[AuthConnector]] which will be used to obtain user details
  *                      from the user-details service. It is evaluated lazily since it
  *                      is declared lazy in [[AuthConnector]]
  */
class UserInfoService(authConnector: ⇒ AuthConnector,
                      citizenDetailsConnector: CitizenDetailsConnector) extends ServicesConfig{

  def getUserInfo(authContext: AuthContext, nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[UserInfo] =
    for {
      userDetails    ← queryUserDetails(authContext)
      citizenDetails ← citizenDetailsConnector.getDetails(nino)
    } yield toUserInfo(userDetails, citizenDetails, nino)


  private def queryUserDetails(authContext: AuthContext)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[UserDetailsResponse] =
    EitherT.right[Future, String, UserDetailsResponse](
      authConnector.getUserDetails[UserDetailsResponse](authContext))

  private def toUserInfo(u: UserDetailsResponse,
                         c: CitizenDetailsResponse,
                         nino: NINO): UserInfo =
    UserInfo(
      u.name + " " + u.lastName,
      nino,
      u.dateOfBirth,
      u.email,
      c.address.toList()
    )

}

object UserInfoService {

  case class UserDetailsResponse(name: String, lastName: String, email: String, dateOfBirth: LocalDate)

  implicit val userDetailsResponseReads: Reads[UserDetailsResponse] = Json.reads[UserDetailsResponse]



}
