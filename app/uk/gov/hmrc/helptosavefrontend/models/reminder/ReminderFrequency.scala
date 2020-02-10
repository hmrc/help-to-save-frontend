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
import uk.gov.hmrc.helptosavefrontend.forms.SortCode
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

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
    name:           String,
    optInStatus:    Boolean   = false,
    daysToReceive:  Seq[Int]  = Seq(),
    nextSendDate:   LocalDate = LocalDate.now(),
    bounceCount:    Int       = 0,
    callBackUrlRef: String    = "")

object HtsUser {
  implicit val htsUserFormat: Format[HtsUser] = Json.format[HtsUser]
  implicit val writes: Writes[HtsUser] = Writes[HtsUser](s ⇒ JsString(s.toString))

}
