/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.helptosavefrontend.models

import uk.gov.hmrc.helptosavefrontend.util.NINO
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.AuditExtensions._
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.config.AppName

trait HTSEvent {
  val value: DataEvent
}

object HTSEvent extends AppName {
  def apply(auditType: String,
            detail:    Map[String, String])(implicit hc: HeaderCarrier): DataEvent =
    DataEvent(appName, auditType = auditType, detail = detail, tags = hc.toAuditTags("", "N/A"))

}

case class EmailChanged(nino: NINO, oldEmail: String, newEmail: String)(implicit hc: HeaderCarrier) extends HTSEvent {
  val value: DataEvent = HTSEvent(
    "EmailChanged",
    Map[String, String]("nino" → nino, "originalEmail" → oldEmail, "newEmail" → newEmail)
  )
}

case class SuspiciousActivity(nino: Option[NINO], activity: String)(implicit hc: HeaderCarrier) extends HTSEvent {
  val value: DataEvent = {
    val details = nino match {
      case Some(p) ⇒ Map[String, String]("nino" → p, "reason" → activity)
      case None    ⇒ Map[String, String]("reason" → activity)
    }
    HTSEvent("SuspiciousActivity", details)
  }
}
