/*
 * Copyright 2020 HM Revenue & Customs
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

import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityCheckResultType.Eligible

sealed trait EligibilityReason

object EligibilityReason {

  case object UCClaimantAndIncomeSufficient extends EligibilityReason

  case class EntitledToWTC(sufficientUCIncome: Boolean) extends EligibilityReason

  def fromEligible(eligible: Eligible): Option[EligibilityReason] =
    eligible.value.eligibilityCheckResult.reasonCode match {
      case 6 ⇒ Some(UCClaimantAndIncomeSufficient) // scalastyle:ignore magic.number
      case 7 ⇒ Some(EntitledToWTC(false)) // scalastyle:ignore magic.number
      case 8 ⇒ Some(EntitledToWTC(true)) // scalastyle:ignore magic.number
      case _ ⇒ None
    }

}
