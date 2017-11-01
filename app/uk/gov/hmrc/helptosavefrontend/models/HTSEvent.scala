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

import cats.instances.int._
import cats.syntax.eq._
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

case class AccountCreated(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier) extends HTSEvent {

  val value: DataEvent = HTSEvent(
    "AccountCreated",
    Map[String, String](
      "forename" → userInfo.forename,
      "surname" → userInfo.surname,
      "dateOfBirth" → userInfo.dateOfBirth.toString,
      "nino" → userInfo.nino,
      "address1" → userInfo.contactDetails.address1,
      "address2" → userInfo.contactDetails.address2,
      "address3" → {
        userInfo.contactDetails.address3.fold("") {
          identity
        }
      },
      "address4" → {
        userInfo.contactDetails.address4.fold("") {
          identity
        }
      },
      "address5" → {
        userInfo.contactDetails.address5.fold("") {
          identity
        }
      },
      "postcode" → userInfo.contactDetails.postcode,
      "countryCode" → {
        userInfo.contactDetails.countryCode.fold("") {
          identity
        }
      },
      "email" → userInfo.contactDetails.email,
      "phoneNumber" → {
        userInfo.contactDetails.phoneNumber.fold("") {
          identity
        }
      },
      "communicationPreference" → userInfo.contactDetails.communicationPreference,
      "registrationChannel" → userInfo.registrationChannel)
  )
}

case class EligibilityResultEvent(nino: NINO, eligibilityResult: EligibilityCheckResult)(implicit hc: HeaderCarrier) extends HTSEvent {

  val value: DataEvent = {
    val response = eligibilityResult.value
    val details =
      if (response.resultCode === 1) {
        Map[String, String]("nino" → nino, "eligible" → "true")
      } else {
        Map[String, String]("nino" → nino, "eligible" → "false",
          "reason" → ("Response: " +
            s"resultCode=${response.resultCode}, reasonCode=${response.reasonCode}, " +
            s"meaning result='${response.result}', reason='${response.reason}'")
        )
      }

    HTSEvent("EligibilityResult", details)
  }
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
