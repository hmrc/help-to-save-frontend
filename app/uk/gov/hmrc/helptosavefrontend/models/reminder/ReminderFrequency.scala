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

package uk.gov.hmrc.helptosavefrontend.models.reminder

import java.time.LocalDate

import play.api.libs.json.{Format, JsPath, JsString, Json, Reads, Writes}
import uk.gov.hmrc.domain.Nino
import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, JsPath, Json, Reads}
case class ReminderFrequency(nino:           Nino,
                             email:          String,
                             name:           String,
                             optInStatus:    Boolean   = false,
                             daysToReceive:  Seq[Int]  = Seq(),
                             nextSendDate:   LocalDate = LocalDate.now(),
                             bounceCount:    Int       = 0,
                             callBackUrlRef: String    = "")

object ReminderFrequency {
  implicit val formats: Format[ReminderFrequency] = Json.format[ReminderFrequency]

}

case class HtsUser(
    nino:           Nino,
    email:          String,
    name:           String    = "",
    optInStatus:    Boolean   = false,
    daysToReceive:  Seq[Int]  = Seq(),
    nextSendDate:   LocalDate = LocalDate.now(),
    bounceCount:    Int       = 0,
    callBackUrlRef: String    = "")

object HtsUser {
  implicit val htsUserFormat: Format[HtsUser] = Json.format[HtsUser]
  implicit val writes: Writes[HtsUser] = Writes[HtsUser](s ⇒ JsString(s.toString))
  implicit val reads: Reads[HtsUser] = (
    (JsPath \ "nino").read[String].orElse((JsPath \ "nino").read[String]).map(Nino.apply(_)) and
    (JsPath \ "email").read[String] and //.orElse((JsPath \ "nino").read[String]).map(Email.apply(_)) and
    (JsPath \ "name").read[String] and
    (JsPath \ "optInStatus").read[Boolean] and
    (JsPath \ "daysToReceive").read[List[Int]] and
    (JsPath \ "nextSendDate").read[LocalDate] and
    (JsPath \ "bounceCount").read[Int] and
    (JsPath \ "callBackUrlRef").read[String]
  )(HtsUser.apply(_, _, _, _, _, _, _, _))

}

object DateToDaysMapper {
  val d2dMapper: Map[String, Seq[Int]] = Map("1st" -> Seq(1),
    "25th" -> Seq(25),
    "1st day and 25th" -> Seq(1, 25),
    "cancel" -> Seq(0))
}

object DaysToDateMapper {
  val reverseMapper: Map[Seq[Int], String] = for ((k, v) ← DateToDaysMapper.d2dMapper) yield (v, k)

}

case class CancelHtsUserReminder(
    nino: String)

object CancelHtsUserReminder {
  implicit val htsUserCancelFormat: Format[CancelHtsUserReminder] = Json.format[CancelHtsUserReminder]
  implicit val writes: Writes[CancelHtsUserReminder] = Writes[CancelHtsUserReminder](s ⇒ JsString(s.toString))
  implicit val reads: Reads[CancelHtsUserReminder] = (
    (JsPath \ "nino").read[String].orElse((JsPath \ "nino").read[String]).map(CancelHtsUserReminder.apply(_))
  )

}
