/*
 * Copyright 2019 HM Revenue & Customs
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

import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityCheckResultType.Eligible
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityReason.{EntitledToWTC, _}

// scalastyle:off magic.number
class EligibilityTypeSpec extends WordSpec with Matchers with GeneratorDrivenPropertyChecks {

  "EligibilityType" must {

    "have a method which converts from Eligible to EligibilityType" in {
        def eligible(reasonCode: Int) = Eligible(EligibilityCheckResponse(EligibilityCheckResult("", 0, "", reasonCode), Some(134.45)))

      EligibilityReason.fromEligible(eligible(6)) shouldBe Some(UCClaimantAndIncomeSufficient)
      EligibilityReason.fromEligible(eligible(7)) shouldBe Some(EntitledToWTC(false))
      EligibilityReason.fromEligible(eligible(8)) shouldBe Some(EntitledToWTC(true))

      forAll { reasonCode: Int ⇒
        whenever(!Set(6, 7, 8).contains(reasonCode)) {
          EligibilityReason.fromEligible(eligible(reasonCode)) shouldBe None
        }

      }
    }
  }
}
