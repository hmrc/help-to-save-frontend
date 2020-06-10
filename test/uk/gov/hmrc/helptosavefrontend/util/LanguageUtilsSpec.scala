/*
 * Copyright 2020 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.helptosavefrontend.util

import play.api.i18n.Lang
import java.time.{LocalDate, LocalTime}

class LanguageUtilsSpec extends UnitSpec {

  "LanguageUtils" should {

    "return a easy reading English date " in {
      val date: LocalDate = LocalDate.of(2020, 6, 10)
      val easyReadingDate: String = LanguageUtils.formatLocalDate(date)(Lang("en"))

      assert( easyReadingDate == "Wednesday 10 June 2020" )
    }

    "return a easy reading Welsh date " in {
      val date: LocalDate = LocalDate.of(2020, 6, 10)
      val easyReadingDate: String = LanguageUtils.formatLocalDate(date)(Lang("cy"))
      assert( easyReadingDate == "Dydd Mercher 10 Mehefin 2020")
    }

    "return a 12 hours timestamp  " in {
      val time: LocalTime = LocalTime.of(14, 16)
      val easyReadingTimestamp: String = LanguageUtils.formatLocalTime(time)

      assert( easyReadingTimestamp == "2:16pm" )
    }

  }

}
