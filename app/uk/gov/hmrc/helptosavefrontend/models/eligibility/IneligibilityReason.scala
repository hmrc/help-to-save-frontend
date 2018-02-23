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

sealed trait IneligibilityReason

object IneligibilityReason {

  case object EntitledToWTCNoTCAndNoUC extends IneligibilityReason

  case object EntitledToWTCNoTCAndInsufficientUC extends IneligibilityReason

  case object NotEntitledToWTCAndNoUC extends IneligibilityReason

  case object NotEntitledToWTCAndUCInsufficient extends IneligibilityReason

  case object NotEntitledToWTCAndUnknownUC extends IneligibilityReason

  case object InvalidNino extends IneligibilityReason

  def fromIneligible(ineligible: Ineligible): Option[IneligibilityReason] = ineligible.value.reasonCode match {
    case 2  ⇒ Some(NotEntitledToWTCAndUnknownUC) // scalastyle:ignore magic.number
    case 3  ⇒ Some(EntitledToWTCNoTCAndNoUC) // scalastyle:ignore magic.number
    case 4  ⇒ Some(EntitledToWTCNoTCAndInsufficientUC) // scalastyle:ignore magic.number
    case 5  ⇒ Some(NotEntitledToWTCAndUCInsufficient) // scalastyle:ignore magic.number
    case -1 ⇒ Some(InvalidNino) // scalastyle:ignore magic.number
    case 9  ⇒ Some(NotEntitledToWTCAndNoUC) // scalastyle:ignore magic.number
    case _  ⇒ None
  }

  implicit val ineligibilityTypeEq: Eq[IneligibilityReason] = new Eq[IneligibilityReason] {
    override def eqv(x: IneligibilityReason, y: IneligibilityReason) = (x, y) match {
      case (EntitledToWTCNoTCAndNoUC, EntitledToWTCNoTCAndNoUC) ⇒ true
      case (EntitledToWTCNoTCAndInsufficientUC, EntitledToWTCNoTCAndInsufficientUC) ⇒ true
      case (NotEntitledToWTCAndNoUC, NotEntitledToWTCAndNoUC) ⇒ true
      case (NotEntitledToWTCAndUCInsufficient, NotEntitledToWTCAndUCInsufficient) ⇒ true
      case (NotEntitledToWTCAndUnknownUC, NotEntitledToWTCAndUnknownUC) ⇒ true
      case (InvalidNino, InvalidNino) ⇒ true
      case _ ⇒ false
    }
  }

}
