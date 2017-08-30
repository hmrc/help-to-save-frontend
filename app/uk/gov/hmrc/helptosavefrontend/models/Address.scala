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

import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.auth.core.retrieve.ItmpAddress

case class Address(lines:    List[String],
                   postcode: Option[String],
                   country:  Option[String])

object Address {

  implicit val addressFormat: Format[Address] = Json.format[Address]

  def apply(itmpAddress: ItmpAddress): Address = Address(
    List(
      itmpAddress.line1,
      itmpAddress.line2,
      itmpAddress.line3,
      itmpAddress.line4,
      itmpAddress.line5
    ).collect { case Some(s) if s.nonEmpty ⇒ s },
    itmpAddress.postCode,
    itmpAddress.countryCode
  )

}
