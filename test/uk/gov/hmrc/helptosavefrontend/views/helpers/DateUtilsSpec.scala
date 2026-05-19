/*
 * Copyright 2026 HM Revenue & Customs
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
import java.time.format.DateTimeFormatter
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.{Lang, Messages, MessagesApi}
import uk.gov.hmrc.helptosavefrontend.views.helpers.DateUtils

class DateUtilsSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  val localDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d/M/uuuu")

  trait EnglishLanguageTest {
    val messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]
    implicit val messages: Messages = messagesApi.preferred(Seq(Lang("en")))
  }

  trait WelshLanguageTest {
    val messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]
    implicit val messages: Messages = messagesApi.preferred(Seq(Lang("cy")))
  }
  "Format Date" should {
    "correctly represent a local date as a string in english" in new EnglishLanguageTest {
      val date: LocalDate = LocalDate.parse("17/7/1980", localDateFormatter)
      DateUtils.formatDate(date) mustBe "Jul 17, 1980"
    }

    "correctly represent a selection of dates as strings in Welsh" in new WelshLanguageTest {
      val date1: LocalDate = LocalDate.parse("1/1/2020", localDateFormatter)
      DateUtils.formatDate(date1) mustBe "Ion 1, 2020"

      val date2: LocalDate = LocalDate.parse("21/06/1999", localDateFormatter)
      DateUtils.formatDate(date2) mustBe "Meh 21, 1999"
    }
  }
}
