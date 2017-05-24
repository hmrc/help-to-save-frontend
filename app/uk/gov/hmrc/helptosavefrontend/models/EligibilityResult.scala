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
import uk.gov.hmrc.helptosavefrontend.models.UserInfo.userDetailsFormat

/**
  * The result of an eligibility check.
  *
  * @param result Whether or not the applicant is eligible for HTS
  */
// implemented as a value case class to get rid of runtime allocation overhead
case class EligibilityResult(result: Option[UserInfo]) extends AnyVal {
  def fold[A](whenIneligible: ⇒ A, whenEligible: UserInfo ⇒ A): A = result.fold(whenIneligible)(whenEligible)
}

object EligibilityResult {
  implicit val format: Format[EligibilityResult] = Json.format[EligibilityResult]
}
