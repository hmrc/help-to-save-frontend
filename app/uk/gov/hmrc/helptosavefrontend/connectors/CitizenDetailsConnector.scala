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
import uk.gov.hmrc.helptosavefrontend.connectors.CitizenDetailsConnector.CitizenDetailsResponse
import uk.gov.hmrc.helptosavefrontend.util._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext

/**
  * A connector which connects to the `citizen-details` microservice to obtain
  * a person's address
  */
@ImplementedBy(classOf[CitizenDetailsConnectorImpl])
trait CitizenDetailsConnector {

  def getDetails(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[CitizenDetailsResponse]
}

object CitizenDetailsConnector {

  case class Person(firstName: Option[String],
                                        lastName: Option[String],
                                        dateOfBirth: Option[LocalDate])

  case class Address(line1: Option[String],
                                         line2: Option[String],
                                         line3: Option[String],
                                         line4: Option[String],
                                         line5: Option[String],
                                         postcode: Option[String],
                                         country: Option[String])

  case class CitizenDetailsResponse(person: Option[Person], address: Option[Address])

  implicit class AddressOps(val a: Address) extends AnyVal {
    def toList(): List[String] =
      List(a.line1, a.line2, a.line3, a.line4, a.line5, a.postcode, a.country)
        .collect{ case Some(s) ⇒ s }
        .map(_.trim)
        .filter(_.nonEmpty)
  }

  implicit val personReads: Reads[Person] = Json.reads[Person]

  implicit val addressReads: Reads[Address] = Json.reads[Address]

  implicit val citizenDetailsResponseReads: Reads[CitizenDetailsResponse] = Json.reads[CitizenDetailsResponse]

}

@Singleton
class CitizenDetailsConnectorImpl extends CitizenDetailsConnector with ServicesConfig {

  private val citizenDetailsBaseURL: String = baseUrl("citizen-details")

  private def citizenDetailsURI(nino: NINO): String = s"$citizenDetailsBaseURL/citizen-details/$nino/designatory-details"

  override def getDetails(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[CitizenDetailsResponse] =
    getResult[CitizenDetailsResponse](citizenDetailsURI(nino))
}