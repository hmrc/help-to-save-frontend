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

package uk.gov.hmrc.helptosavefrontend.models.reminder

import java.time.LocalDate

import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.domain.Nino
case class ReminderFrequency(
  nino: Nino,
  email: String,
  name: String,
  optInStatus: Boolean = false,
  daysToReceive: Seq[Int] = Seq(),
  nextSendDate: LocalDate = LocalDate.now(),
  callBackUrlRef: String = ""
)

object ReminderFrequency {
  implicit val formats: Format[ReminderFrequency] = Json.format[ReminderFrequency]

}

case class HtsUserSchedule(
  nino: Nino,
  email: String,
  firstName: String = "",
  lastName: String = "",
  optInStatus: Boolean = false,
  daysToReceive: Seq[Int] = Seq(),
  nextSendDate: LocalDate = LocalDate.now(),
  callBackUrlRef: String = "",
  endDate: Option[LocalDate] = None
)

object HtsUserSchedule {
  implicit val htsUserFormat: Format[HtsUserSchedule] = Json.format[HtsUserSchedule]
  implicit val writes: Writes[HtsUserSchedule] = Writes[HtsUserSchedule](s ⇒ JsString(s.toString))
  implicit val reads: Reads[HtsUserSchedule] = (
    (JsPath \ "nino").read[String].orElse((JsPath \ "nino").read[String]).map(Nino.apply(_)) and
      (JsPath \ "email").read[String] and //.orElse((JsPath \ "nino").read[String]).map(Email.apply(_)) and
      (JsPath \ "firstName").read[String] and
      (JsPath \ "lastName").read[String] and
      (JsPath \ "optInStatus").read[Boolean] and
      (JsPath \ "daysToReceive").read[List[Int]] and
      (JsPath \ "nextSendDate").read[LocalDate] and
      (JsPath \ "callBackUrlRef").read[String] and
      (JsPath \ "accountClosingDate").readNullable[LocalDate]
  )(HtsUserSchedule.apply(_, _, _, _, _, _, _, _, _))

}

object DateToDaysMapper {
  val d2dMapper: Map[String, Seq[Int]] =
    Map("1st" -> Seq(1), "25th" -> Seq(25), "1st day and 25th" -> Seq(1, 25), "cancel" -> Seq(0))
}

object DaysToDateMapper {
  val reverseMapper: Map[Seq[Int], String] = for ((k, v) ← DateToDaysMapper.d2dMapper) yield (v, k)

}

case class CancelHtsUserReminder(nino: String)

object CancelHtsUserReminder {
  implicit val htsUserCancelFormat: Format[CancelHtsUserReminder] = Json.format[CancelHtsUserReminder]
  implicit val writes: Writes[CancelHtsUserReminder] = Writes[CancelHtsUserReminder](s ⇒ JsString(s.toString))
  implicit val reads: Reads[CancelHtsUserReminder] = (
    (JsPath \ "nino").read[String].orElse((JsPath \ "nino").read[String]).map(CancelHtsUserReminder.apply(_))
  )
}
case class UpdateReminderEmail(nino: String, email: String, firstName: String, lastName: String)

object UpdateReminderEmail {

  implicit val htsUpdateEmailFormat: Format[UpdateReminderEmail] = Json.format[UpdateReminderEmail]

  implicit val writes: Writes[UpdateReminderEmail] = Writes[UpdateReminderEmail](s ⇒ JsString(s.toString))

  implicit val reads: Reads[UpdateReminderEmail] = (
    (JsPath \ "nino").read[String].orElse((JsPath \ "nino").read[String]) and
      (JsPath \ "email").read[String] and
      (JsPath \ "firstName").read[String] and
      (JsPath \ "lastName").read[String]
  )(UpdateReminderEmail.apply(_, _, _, _))

}
