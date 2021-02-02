/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.helptosavefrontend.util

import java.time.LocalDateTime

class MaintenenceScheduleSpec extends UnitSpec {

  "MaintenenceSchedule" must {
    "return the last end time" in {
      val schedule = "2020-06-03T12:50/2020-06-03T13:01,2020-06-03T12:51/2020-06-03T13:02"
      val original = Schedule.parse(schedule).endOfMaintenance(LocalDateTime.parse("2020-06-03T12:52"))
      val expected = Some(LocalDateTime.parse("2020-06-03T13:02"))

      original shouldBe expected
    }

    "return none when outside maintenance window" in {
      val schedule = "2020-06-03T12:50/2020-06-03T13:01"
      val original = Schedule.parse(schedule).endOfMaintenance(LocalDateTime.parse("2020-06-03T13:03"))
      val expected = None

      original shouldBe expected
    }

  }
}
