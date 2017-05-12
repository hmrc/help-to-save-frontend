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

package uk.gov.hmrc.helptosavefrontend.connectors

import java.time.LocalDate

import com.google.inject.{ImplementedBy, Singleton}
import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.helptosavefrontend.connectors.UserDetailsConnector.UserDetailsResponse
import uk.gov.hmrc.helptosavefrontend.util._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

@ImplementedBy(classOf[UserDetailsConnectorImpl])
trait UserDetailsConnector {
  def getUserDetails(userDetailsUri: String)(implicit hc: HeaderCarrier): Result[UserDetailsResponse]
}

object UserDetailsConnector {

  case class UserDetailsResponse(name: String,
                                 lastName: Option[String],
                                 email: Option[String],
                                 dateOfBirth: Option[LocalDate])

  implicit val userDetailsResponseReads: Reads[UserDetailsResponse] = Json.reads[UserDetailsResponse]
}

@Singleton
class UserDetailsConnectorImpl extends UserDetailsConnector with ServicesConfig {
  override def getUserDetails(userDetailsUri: String)(implicit hc: HeaderCarrier): Result[UserDetailsResponse] = {
    getResult[UserDetailsResponse](userDetailsUri)
  }
}
