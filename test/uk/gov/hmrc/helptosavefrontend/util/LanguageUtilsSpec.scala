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

package uk.gov.hmrc.helptosavefrontend.util

import play.api.i18n.Lang

import java.time.{LocalDate, LocalTime}

class LanguageUtilsSpec extends UnitSpec {

  "LanguageUtils" should {

    "return a easy reading English date " in {
      val date: LocalDate = LocalDate.of(2020, 6, 10)
      val easyReadingDate: String = LanguageUtils.formatLocalDate(date)(Lang("en"))

      assert(easyReadingDate == "Wednesday 10 June 2020")
    }

    "return a easy reading Welsh date " in {
      val date: LocalDate = LocalDate.of(2020, 6, 10)
      val easyReadingDate: String = LanguageUtils.formatLocalDate(date)(Lang("cy"))
      assert(easyReadingDate == "Dydd Mercher 10 Mehefin 2020")
    }

    "return a 12 hours timestamp  " in {
      val time: LocalTime = LocalTime.of(14, 16)
      val easyReadingTimestamp: String = LanguageUtils.formatLocalTime(time)

      assert(easyReadingTimestamp == "2:16pm")
    }

  }

}
