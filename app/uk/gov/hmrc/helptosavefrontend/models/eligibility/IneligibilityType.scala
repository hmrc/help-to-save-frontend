/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.helptosavefrontend.models.eligibility

import cats.Eq
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityCheckResult.Ineligible

sealed trait IneligibilityType

object IneligibilityType {

  case object NotEntitledToWTC extends IneligibilityType

  case object EntitledToWTCButNilTC extends IneligibilityType

  case object InvalidNino extends IneligibilityType

  case object Unknown extends IneligibilityType

  def fromIneligible(ineligible: Ineligible): IneligibilityType = ineligible.value.reasonCode match {
    case 2 | 4 ⇒ NotEntitledToWTC // scalastyle:ignore magic.number
    case 3 | 5 ⇒ EntitledToWTCButNilTC // scalastyle:ignore magic.number
    case -1    ⇒ InvalidNino // scalastyle:ignore magic.number
    case _     ⇒ Unknown
  }

  implicit val ineligibilityTypeEq: Eq[IneligibilityType] = new Eq[IneligibilityType] {
    override def eqv(x: IneligibilityType, y: IneligibilityType) = (x, y) match {
      case (NotEntitledToWTC, NotEntitledToWTC) ⇒ true
      case (EntitledToWTCButNilTC, EntitledToWTCButNilTC) ⇒ true
      case (InvalidNino, InvalidNino) ⇒ true
      case (Unknown, Unknown) ⇒ true
      case _ ⇒ false
    }
  }

}
