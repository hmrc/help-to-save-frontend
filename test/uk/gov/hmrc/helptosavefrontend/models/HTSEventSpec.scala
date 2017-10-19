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

import play.api.Logger
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.models.EligibilityCheckResult.{AlreadyHasAccount, Eligible}
import uk.gov.hmrc.helptosavefrontend.models.TestData.UserData.validNSIUserInfo
import uk.gov.hmrc.http.HeaderCarrier

class HTSEventSpec extends TestSupport {

  val appName = "help-to-save-frontend"

  "AccountCreated" must {
    "be created with the appropriate auditSource" in {
      val event = AccountCreated(validNSIUserInfo)(new HeaderCarrier)
      event.value.auditSource shouldBe appName
    }

    "be created with the appropriate auditType" in {
      val event = AccountCreated(validNSIUserInfo)(new HeaderCarrier)
      event.value.auditType shouldBe "AccountCreated"
    }

    "have the correct detail" in {
      val addr4 = "Somewhere"
      val addr5 = "Someplace"
      val cc = "GB"
      val pNumber = "01254-888888"
      val completeUserInfo =
        validNSIUserInfo copy (contactDetails =
          validNSIUserInfo.contactDetails copy (address4 = Some(addr4),
            address5 = Some(addr5),
            countryCode = Some(cc),
            email = "this@that.com",
            phoneNumber = Some(pNumber),
            communicationPreference = "02"))

      val event = AccountCreated(completeUserInfo)(new HeaderCarrier)
      event.value.detail.size shouldBe 15
      event.value.detail.exists { case (k, v) ⇒ k === "forename" && v === completeUserInfo.forename } shouldBe true
      event.value.detail.exists(x ⇒ x._1 === "surname" && x._2 === completeUserInfo.surname) shouldBe true
      event.value.detail.exists(x ⇒ x._1 === "dateOfBirth" && x._2 === completeUserInfo.dateOfBirth.toString) shouldBe true
      event.value.detail.exists(x ⇒ x._1 === "nino" && x._2 === completeUserInfo.nino) shouldBe true
      event.value.detail.exists(x ⇒ x._1 === "address1" && x._2 === completeUserInfo.contactDetails.address1) shouldBe true
      event.value.detail.exists(x ⇒ x._1 === "address2" && x._2 === completeUserInfo.contactDetails.address2) shouldBe true
      event.value.detail.exists(x ⇒ x._1 === "address3" && x._2 === "Westeros") shouldBe true
      event.value.detail.exists(x ⇒ x._1 === "address4" && x._2 === addr4) shouldBe true
      event.value.detail.exists(x ⇒ x._1 === "address5" && x._2 === addr5) shouldBe true
      event.value.detail.exists(x ⇒ x._1 === "postcode" && x._2 === completeUserInfo.contactDetails.postcode) shouldBe true
      event.value.detail.exists(x ⇒ x._1 === "countryCode" && x._2 === cc) shouldBe true
      event.value.detail.exists(x ⇒ x._1 === "email" && x._2 === completeUserInfo.contactDetails.email) shouldBe true
      event.value.detail.exists(x ⇒ x._1 === "phoneNumber" && x._2 === pNumber) shouldBe true
      event.value.detail.exists(x ⇒ x._1 === "communicationPreference" && x._2 === completeUserInfo.contactDetails.communicationPreference) shouldBe true
      event.value.detail.exists(x ⇒ x._1 === "registrationChannel" && x._2 === completeUserInfo.registrationChannel) shouldBe true
    }
  }

  "EligibilityResult" must {
    val eligibilityResult = AlreadyHasAccount(EligibilityCheckResponse("HtS account was previously created", 3, "HtS account already exists", 1))
    "be created with the appropriate auditSource" in {
      val event = EligibilityResultEvent(validNSIUserInfo.nino, eligibilityResult)(new HeaderCarrier)
      event.value.auditSource shouldBe appName
    }

    "be created with the appropriate auditType" in {
      val event = EligibilityResultEvent(validNSIUserInfo.nino, eligibilityResult)(new HeaderCarrier)
      event.value.auditType shouldBe "EligibilityResult"
    }

    "be created with details in the correct format" in {
      val event = EligibilityResultEvent(validNSIUserInfo.nino, eligibilityResult)(new HeaderCarrier)
      Logger.info(s"details= ${event.value.detail}")
      event.value.detail.size shouldBe 2
      event.value.detail.exists(x ⇒ x._1 === "nino" && x._2 === validNSIUserInfo.nino) shouldBe true
      event.value.detail.exists(x ⇒ x._1 === "reason" && x._2 === "3, 1, meaning 'HtS account was previously created', 'HtS account already exists'") shouldBe true
    }
  }

  "EmailChanged" must {
    "be created with the appropriate auditSource and auditDetails" in {
      val event = EmailChanged("NINO", "new-email@test.com")(new HeaderCarrier)
      event.value.auditSource shouldBe appName
      event.value.auditType shouldBe "EmailChanged"
      event.value.detail shouldBe Map[String, String]("nino" -> "NINO", "reason" -> "User has changed email address to: new-email@test.com")
    }
  }

  "SuspiciousActivity" must {
    "be created with the appropriate auditSource and auditDetails" in {
      val event = SuspiciousActivity("nino", "nino_mismatch")(new HeaderCarrier)
      event.value.auditSource shouldBe appName
      event.value.auditType shouldBe "SuspiciousActivity"
      event.value.detail shouldBe Map[String, String]("nino" -> "nino", "reason" -> "nino_mismatch")
    }
  }
}
