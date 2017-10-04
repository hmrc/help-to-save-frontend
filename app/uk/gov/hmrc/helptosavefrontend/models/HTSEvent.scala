/*
 * Copyright 2017 HM Revenue & Customs
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

object HTSEvent {
  def apply(auditType: String,
            detail:    Map[String, String])(implicit hc: HeaderCarrier): DataEvent =
    DataEvent(AppName.appName, auditType = auditType, detail = detail, tags = hc.toAuditTags("", "N/A"))

}

case class AccountCreated(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier) extends HTSEvent {

  val value: DataEvent = HTSEvent(
    "AccountCreated",
    Map[String, String](
      "forename" -> userInfo.forename,
      "surname" -> userInfo.surname,
      "dateOfBirth" -> userInfo.dateOfBirth.toString,
      "nino" -> userInfo.nino,
      "address1" -> userInfo.contactDetails.address1,
      "address2" -> userInfo.contactDetails.address2,
      "address3" -> {
        userInfo.contactDetails.address3.fold("") {
          identity
        }
      },
      "address4" -> {
        userInfo.contactDetails.address4.fold("") {
          identity
        }
      },
      "address5" -> {
        userInfo.contactDetails.address5.fold("") {
          identity
        }
      },
      "postcode" -> userInfo.contactDetails.postcode,
      "countryCode" -> {
        userInfo.contactDetails.countryCode.fold("") {
          identity
        }
      },
      "email" -> userInfo.contactDetails.email,
      "phoneNumber" -> {
        userInfo.contactDetails.phoneNumber.fold("") {
          identity
        }
      },
      "communicationPreference" -> userInfo.contactDetails.communicationPreference,
      "registrationChannel" -> userInfo.registrationChannel)
  )
}

case class EligibilityResult(nino: NINO, reason: String, isEligible: Boolean = true)(implicit hc: HeaderCarrier) extends HTSEvent {
  val value: DataEvent = HTSEvent(
    "EligibilityResult",
    Map[String, String]("nino" -> nino, "reason" -> reason, "eligible" -> isEligible.toString)
  )
}

case class EmailChanged(nino: NINO, oldEmail: String, newEmail: String)(implicit hc: HeaderCarrier) extends HTSEvent {
  val value: DataEvent = HTSEvent(
    "EmailChanged",
    Map[String, String]("nino" -> nino, "oldEmail" -> oldEmail, "newEmail" -> newEmail)
  )
}

case class SuspiciousActivity(nino: NINO, reason: String)(implicit hc: HeaderCarrier) extends HTSEvent {
  val value: DataEvent = HTSEvent(
    "SuspiciousActivity",
    Map[String, String]("nino" -> nino, "reason" -> reason)
  )
}
