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

package uk.gov.hmrc.helptosavefrontend.models

import play.api.libs.json.{Format, JsValue, Json}
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.util.NINO
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.AuditExtensions._
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

trait HTSEvent {
  val value: ExtendedDataEvent
}

object HTSEvent {
  def apply(appName: String, auditType: String, detail: JsValue, transactionName: String, path: String)(
    implicit hc: HeaderCarrier
  ): ExtendedDataEvent =
    ExtendedDataEvent(appName, auditType = auditType, detail = detail, tags = hc.toAuditTags(transactionName, path))

}

case class EmailChanged(
  nino: NINO,
  oldEmail: String,
  newEmail: String,
  duringRegistrationJourney: Boolean,
  path: String
)(implicit hc: HeaderCarrier, appConfig: FrontendAppConfig)
    extends HTSEvent {
  val value: ExtendedDataEvent = HTSEvent(
    appConfig.appName,
    "EmailChanged",
    Json.toJson(EmailChanged.Details(nino, oldEmail, newEmail, duringRegistrationJourney)),
    "email-changed",
    path
  )
}

object EmailChanged {

  private case class Details(nino: String, originalEmail: String, newEmail: String, duringRegistrationJourney: Boolean)

  private implicit val detailsFormat: Format[Details] = Json.format[Details]

}

case class SuspiciousActivity(nino: Option[NINO], activity: String, path: String)(
  implicit hc: HeaderCarrier,
  appConfig: FrontendAppConfig
) extends HTSEvent {
  val value: ExtendedDataEvent =
    HTSEvent(
      appConfig.appName,
      "SuspiciousActivity",
      Json.toJson(SuspiciousActivity.Details(nino, activity)),
      "suspicious-activity",
      path
    )

}

object SuspiciousActivity {

  private case class Details(nino: Option[String], reason: String)

  private implicit val detailsFormat: Format[Details] = Json.format[Details]
}
