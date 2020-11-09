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

package uk.gov.hmrc.helptosavefrontend.views.helpers

import java.time.LocalDate
import play.api.i18n.Messages

object DateHelper {

  implicit def intToStr: Int => String = _.toString

  def month(date: LocalDate)(implicit messages: Messages): String = {
    date.getMonthValue match {
      case 1 => messages("hts.month.january")
      case 2 => messages("hts.month.february")
      case 3 => messages("hts.month.march")
      case 4 => messages("hts.month.april")
      case 5 => messages("hts.month.may")
      case 6 => messages("hts.month.june")
      case 7 => messages("hts.month.july")
      case 8 => messages("hts.month.august")
      case 9 => messages("hts.month.september")
      case 10 => messages("hts.month.october")
      case 11 => messages("hts.month.november")
      case 12 => messages("hts.month.december")
    }
  }


  def date(date: LocalDate)(implicit messages: Messages): String = {
    s"${date.getDayOfMonth} ${month(date)} ${date.getYear}"
  }


}

