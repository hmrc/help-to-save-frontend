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

package uk.gov.hmrc.helptosavefrontend.views.helpers

import play.api.i18n.Messages

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object DateUtils {

  private val dayExtractor: DateTimeFormatter = DateTimeFormatter.ofPattern("d")
  private val monthExtractor: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM")
  private val yearExtractor: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy")

  def toLocalisedStringMonth(date: LocalDate)(implicit messages: Messages): String = {
    val monthKey = date.format(monthExtractor)
    messages.translate(s"hts.month.$monthKey", Seq.empty).getOrElse(monthKey)
  }

  def toLocalisedString(date: LocalDate)(implicit messages: Messages): String = {
    val monthValue: String = toLocalisedStringMonth(date)
    s"${date.format(dayExtractor)} $monthValue ${date.format(yearExtractor)}"
  }
}
