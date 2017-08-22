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
import uk.gov.hmrc.play.test.UnitSpec

class EmailVerificationRequestSpec extends UnitSpec with TestSupport {


  "the constructor" should {

    "check that the nino and the new email address have been passed in as template parameters" in {
      val email = "aemail@gmail.com"
      val nino = "AE1234XXX"
      val emailVerificationRequest = EmailVerificationRequest(email, nino, "templateID", "P1D", "http;//continue.url.com", Map())
      emailVerificationRequest.templateParameters.getOrElse("email", "") shouldBe email
      emailVerificationRequest.templateParameters.getOrElse("nino", "") shouldBe nino
    }
  }
}
