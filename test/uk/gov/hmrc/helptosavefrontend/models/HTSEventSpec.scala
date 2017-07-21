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

import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.play.http.HeaderCarrier

/**
  * Created by andy on 21/07/2017.
  */
class HTSEventSpec extends TestSupport {
  "ApplicationSubmittedEvent" must {
    "be created with the appropriate auditSource" in {
      val event = new ApplicationSubmittedEvent(validNSIUserInfo)(new HeaderCarrier)
      event.auditSource shouldBe "hts-frontend"
    }

    "be created with the appropriate auditType" in {
      val event = new ApplicationSubmittedEvent(validNSIUserInfo)(new HeaderCarrier)
      event.auditType shouldBe "applicationSubmitted"
    }

    "have the correct detail" in {
      val event = new ApplicationSubmittedEvent(validNSIUserInfo)(new HeaderCarrier)
      event.detail.size shouldBe 4
      event.detail.exists(x => x._1 == "forename" && x._2 == validNSIUserInfo.forename) shouldBe true
      event.detail.exists(x => x._1 == "surname" && x._2 == validNSIUserInfo.surname) shouldBe true
      event.detail.exists(x => x._1 == "dateOfBirth" && x._2 == validNSIUserInfo.dateOfBirth) shouldBe true
      event.detail.exists(x => x._1 == "nino" && x._2 == validNSIUserInfo.nino) shouldBe true


//      event.detail.exists(x => x._1 == "serviceFeel" && x._2 == serviceFeel) shouldBe true
//      event.detail.exists(x => x._1 == "comments" && x._2 == comments) shouldBe true
//      event.detail.exists(x => x._1 == "fullName" && x._2 == fullName) shouldBe true
//      event.detail.exists(x => x._1 == "email" && x._2 == email) shouldBe true
//      event.detail.exists(x => x._1 == "phoneNumber" && x._2 == phoneNumber) shouldBe true
    }
  }
}
