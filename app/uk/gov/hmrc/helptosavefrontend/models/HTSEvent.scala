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

import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.audit.AuditExtensions._


//case class NSIUserInfo (forename: String,
//                        surname: String,
//                        dateOfBirth: LocalDate,
//                        nino: String,
//                        contactDetails: ContactDetails,
//                        registrationChannel: String = "online")
//
//object NSIUserInfo {
//
//    case class ContactDetails(address1: String,
//                              address2: String,
//                              address3: Option[String],
//                              address4: Option[String],
//                              address5: Option[String],
//                              postcode: String,
//                              countryCode: Option[String],
//                              email: String,
//                              phoneNumber: Option[String] = None,
//                              communicationPreference: String = "02")

abstract class HTSEvent(auditType: String, detail: Map[String, String])(implicit hc: HeaderCarrier)
  extends DataEvent(auditSource = "hts-frontend", auditType = auditType, detail = detail, tags = hc.toAuditTags("", "N/A"))

class ApplicationSubmittedEvent(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier)
  extends HTSEvent("applicationSubmitted",
    Map("forename" -> userInfo.forename,
        "surname" -> userInfo.surname,
        "nino" -> userInfo.nino
        ))