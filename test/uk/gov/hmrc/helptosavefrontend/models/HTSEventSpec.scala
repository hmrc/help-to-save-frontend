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

import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.models.TestData.UserData.validNSIUserInfo
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.config.AppName

class HTSEventSpec extends TestSupport with AppName {

  "EmailChanged" must {
    "be created with the appropriate auditSource and auditDetails" in {
      val event = EmailChanged(validNSIUserInfo.nino, "old-email@test.com", "new-email@test.com")(new HeaderCarrier)
      event.value.auditSource shouldBe appName
      event.value.auditType shouldBe "EmailChanged"
      event.value.detail shouldBe Map[String, String]("nino" -> validNSIUserInfo.nino, "originalEmail" -> "old-email@test.com", "newEmail" -> "new-email@test.com")
    }
  }

  "SuspiciousActivity" must {
    "be created with the appropriate auditSource and auditDetails incase of nino_mismatch" in {
      val event = SuspiciousActivity(None, "nino_mismatch, expected foo, received bar")(new HeaderCarrier)
      event.value.auditSource shouldBe appName
      event.value.auditType shouldBe "SuspiciousActivity"
      event.value.detail shouldBe Map[String, String]("reason" -> "nino_mismatch, expected foo, received bar")
    }

    "be created with the appropriate auditSource and auditDetails incase of missing_email_record" in {
      val event = SuspiciousActivity(Some(validNSIUserInfo.nino), "missing_email_record")(new HeaderCarrier)
      event.value.auditSource shouldBe appName
      event.value.auditType shouldBe "SuspiciousActivity"
      event.value.detail shouldBe Map[String, String]("nino" -> validNSIUserInfo.nino, "reason" -> "missing_email_record")
    }
  }
}
