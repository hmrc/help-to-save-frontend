/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.helptosavefrontend.models.account

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.{Clock, LocalDate}

class AccountSpec extends AnyWordSpec with Matchers {

  "Account" must {

    "have a method to get the current bonus term" in {
      val dummyDate = LocalDate.ofEpochDay(0L)
      val today = LocalDate.now(Clock.systemUTC())

      val bonusTerm1 = BonusTerm(0, 0, today.minusDays(2L), dummyDate)
      val bonusTerm2 = BonusTerm(0, 0, today.minusDays(1L), dummyDate)
      val bonusTerm3 = BonusTerm(0, 0, today, dummyDate)
      val bonusTerm4 = BonusTerm(0, 0, today.plusDays(1L), dummyDate)

      val account1 = Account(
        false,
        0,
        0,
        0,
        0,
        dummyDate,
        List(bonusTerm3, bonusTerm4)
      )

      account1.currentBonusTerm() shouldBe Some(bonusTerm4)

      // test when there all bonus terms ends today
      val account2 = account1.copy(bonusTerms = List(bonusTerm2, bonusTerm3))
      account2.currentBonusTerm() shouldBe None

      // test when there all bonus terms are all past ones
      val account3 = account1.copy(bonusTerms = List(bonusTerm1, bonusTerm2))
      account3.currentBonusTerm() shouldBe None

    }

  }

}
