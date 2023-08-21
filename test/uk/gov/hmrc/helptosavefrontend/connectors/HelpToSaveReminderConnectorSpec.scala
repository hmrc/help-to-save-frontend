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

package uk.gov.hmrc.helptosavefrontend.connectors

import java.time.LocalDate

import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.helptosavefrontend.controllers.ControllerSpecWithGuiceApp
import uk.gov.hmrc.helptosavefrontend.models.reminder.{CancelHtsUserReminder, HtsUserSchedule, UpdateReminderEmail}
import uk.gov.hmrc.http.HttpResponse


// scalastyle:off magic.number
class HelpToSaveReminderConnectorSpec
    extends ControllerSpecWithGuiceApp with HttpSupport with ScalaCheckDrivenPropertyChecks {

  lazy val connector: HelpToSaveReminderConnector = new HelpToSaveReminderConnectorImpl(mockHttp)

  val htsReminderURL = "http://localhost:7008"

  val UpdateHtsURL =
    s"$htsReminderURL/help-to-save-reminder/update-htsuser-entity"

  def getHtsReminderUserURL(nino: String) = s"$htsReminderURL/help-to-save-reminder/gethtsuser/$nino"

  val cancelHtsReminderURL = s"$htsReminderURL/help-to-save-reminder/delete-htsuser-entity"

  val emailUpdateHtsReminderURL = s"$htsReminderURL/help-to-save-reminder/update-htsuser-email"

  val emptyBody = ""
  val emptyHeaders :Map[String, Seq[String]] = Map.empty
  implicit val unitFormat: Format[Unit] = new Format[Unit] {
    override def writes(o: Unit) = JsNull

    override def reads(json: JsValue) = json match {
      case JsNull => JsSuccess(())
      case _ => JsError("JSON was not null")
    }
  }

  "validating HtsUser" must {

    val nino: Nino = Nino("AE123456D")

    "return http response as it is to the caller" in {
      val htsUser =
        HtsUserSchedule(nino, "user@gmail.com", "Tyrion", "Lannister", true, Seq(1), LocalDate.parse("2000-01-01"))

      val response =
        HttpResponse(200, Json.toJson(htsUser), emptyHeaders)
      mockPost(UpdateHtsURL, Map.empty, htsUser)(Some(response))
      val result = connector.updateHtsUser(htsUser)
      await(result.value) should equal(Right(htsUser))

    }
  }
  "get HtsUser" must {

    val ninoNew = "AE123456D"
    val nino: Nino = Nino("AE123456D")

    "return http response as it is to the caller" in {
      val htsUser =
        HtsUserSchedule(nino, "user@gmail.com", "Tyrion", "Lannister", true, Seq(1), LocalDate.parse("2000-01-01"))

      val response =
        HttpResponse(200, Json.toJson(htsUser), emptyHeaders)
      mockGet(getHtsReminderUserURL(ninoNew), Map.empty)(Some(response))
      val result = connector.getHtsUser(ninoNew)
      await(result.value) should equal(Right(htsUser))

    }
  }
  "cancel HtsUser Reminder" must {

    val ninoNew = "AE123456D"
    val cancelHtsUserReminder = CancelHtsUserReminder(ninoNew)

    "return http response as it is to the caller" in {
      val response = HttpResponse(200, emptyBody)
      mockPost(cancelHtsReminderURL, Map.empty, cancelHtsUserReminder)(Some(response))
      val result = connector.cancelHtsUserReminders(cancelHtsUserReminder)
      await(result.value) should equal(Right(()))

    }
    "return http response as it is to the caller when not modified" in {
      val response = HttpResponse(304, emptyBody)
      mockPost(cancelHtsReminderURL, Map.empty, cancelHtsUserReminder)(Some(response))
      val result = connector.cancelHtsUserReminders(cancelHtsUserReminder)
      await(result.value) should equal(Right(()))
    }
    "fail when unexpected response received" in {
      val response = HttpResponse(400, emptyBody)
      mockPost(cancelHtsReminderURL, Map.empty, cancelHtsUserReminder)(Some(response))
      val result = connector.cancelHtsUserReminders(cancelHtsUserReminder)
      await(result.value).isLeft should equal(true)
    }
  }

  "Update HtsUser Reminder Email" must {

    val ninoNew = "AE123456D"
    val email = "test@user.com"
    val updateReminderEmail = UpdateReminderEmail(ninoNew, email, "Tyrion", "Lannister")

    "return http response as it is to the caller" in {
      val response =
        HttpResponse(200, emptyBody)
      mockPost(emailUpdateHtsReminderURL, Map.empty, updateReminderEmail)(Some(response))
      val result = connector.updateReminderEmail(updateReminderEmail)
      await(result.value) should equal(Right(()))

    }
  }

}
