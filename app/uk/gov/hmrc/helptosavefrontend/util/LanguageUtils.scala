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

import play.api.i18n.Lang
import java.time.{LocalDate, LocalTime}
import java.text.SimpleDateFormat

object LanguageUtils {
  private val MonthNamesInWelsh = Seq(
    "Ionawr",
    "Chwefror",
    "Mawrth",
    "Ebrill",
    "Mai",
    "Mehefin",
    "Gorffennaf",
    "Awst",
    "Medi",
    "Hydref",
    "Tachwedd",
    "Rhagfyr"
  )
  private val WeekDaysInWelsh =
    Seq("Dydd Llun", "Dydd Mawrth", "Dydd Mercher", "Dydd Iau", "Dydd Gwener", "Dydd Sadwrn", "Dydd Sul")
  def formatLocalDate(localDate: LocalDate)(implicit lang: Lang): String =
    lang.code match {
      case "cy" =>
        s"${WeekDaysInWelsh(localDate.getDayOfWeek.getValue - 1)} ${localDate.getDayOfMonth} ${MonthNamesInWelsh(
          localDate.getMonthValue - 1
        )} ${localDate.getYear}"
      case "en" =>
        new SimpleDateFormat("EEEEE d MMMMM yyyy").format(new SimpleDateFormat("yyyy-MM-dd").parse(localDate.toString))
    }
  def formatLocalTime(time: LocalTime): String =
    new SimpleDateFormat("h:mma").format(new SimpleDateFormat("HH:mm").parse(time.toString)).toLowerCase
}
