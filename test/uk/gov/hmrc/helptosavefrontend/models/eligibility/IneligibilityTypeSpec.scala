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
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityCheckResultType.Ineligible
import uk.gov.hmrc.helptosavefrontend.models.eligibility.IneligibilityReason._

// scalastyle:off magic.number
class IneligibilityTypeSpec extends WordSpec with Matchers with GeneratorDrivenPropertyChecks {

  "IneligibilityType" must {

    "have a method which converts from Ineligible to IneligibilityType" in {
        def ineligible(reasonCode: Int) = Ineligible(EligibilityCheckResponse(EligibilityCheckResult("", 0, "", reasonCode), Some(134.45)))

      IneligibilityReason.fromIneligible(ineligible(3)) shouldBe Some(EntitledToWTCNoTCAndNoUC)
      IneligibilityReason.fromIneligible(ineligible(4)) shouldBe Some(EntitledToWTCNoTCAndInsufficientUC)
      IneligibilityReason.fromIneligible(ineligible(5)) shouldBe Some(NotEntitledToWTCAndUCInsufficient)
      IneligibilityReason.fromIneligible(ineligible(9)) shouldBe Some(NotEntitledToWTCAndNoUC)

      forAll{ reasonCode: Int ⇒
        whenever(!Set(2, 3, 4, 5, -1, 9).contains(reasonCode)){
          IneligibilityReason.fromIneligible(ineligible(reasonCode)) shouldBe None
        }

      }
    }

    "have an Eq instance" in {
      val list = List[IneligibilityReason](EntitledToWTCNoTCAndNoUC, EntitledToWTCNoTCAndInsufficientUC,
                                           NotEntitledToWTCAndUCInsufficient, NotEntitledToWTCAndNoUC)

      val uniquePairs: List[(IneligibilityReason, IneligibilityReason)] =
        list.combinations(2).toList.flatMap(_.permutations.toList).map{ case a :: b :: Nil ⇒ a → b }

      val samePairs: List[(IneligibilityReason, IneligibilityReason)] =
        list.zip(list)

      val equalityPairs = (uniquePairs ::: samePairs).filter{ case (a, b) ⇒ IneligibilityReason.ineligibilityTypeEq.eqv(a, b) }

      equalityPairs shouldBe samePairs

    }

  }

}
// scalastyle:on magic.number
