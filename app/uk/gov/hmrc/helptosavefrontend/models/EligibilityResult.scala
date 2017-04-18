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

import play.api.libs.json.{Format, JsResult, JsValue, Json}
import uk.gov.hmrc.helptosavefrontend.models.UserDetails.userDetailsFormat

/**
  * The result of an eligibility check.
  *
  * @param value `None` corresponds to a result indicating the user is not eligible,
  *              whereas the `Some` case corresponds to the case when  the user is
  *              eligible and contains the necessary data to create the account on NS&I.
  */
// implemented as a value case class to get rid of runtime allocation overhead
case class EligibilityResult(value: Option[UserDetails]) extends AnyVal {
  def fold[A](ineligible: ⇒ A, eligible: UserDetails ⇒ A): A = value.fold(ineligible)(eligible)
}

object EligibilityResult {
  implicit val format: Format[EligibilityResult] = new Format[EligibilityResult]{
    implicit val optionUserFormat: Format[Option[UserDetails]] = Format.optionWithNull[UserDetails]

    override def writes(o: EligibilityResult): JsValue =
      Json.toJson(o.value)

    override def reads(json: JsValue): JsResult[EligibilityResult] =
      Json.fromJson[Option[UserDetails]](json).map(new EligibilityResult(_))
  }
}
