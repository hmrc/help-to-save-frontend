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

package uk.gov.hmrc.helptosavefrontend.util

import java.nio.charset.Charset
import java.util.Base64

import uk.gov.hmrc.helptosavefrontend.TestSupport

class EmailVerificationParamsSpec extends TestSupport {

  val nino = "AE1234XXX"
  val email = "email@gmail.com"
  val params = EmailVerificationParams(nino, email)

  "EmailVerificationParams" must {
    "have an encode method that produces a base64 string" in {
      val result = params.encode
      result.length > 1 shouldBe true
      result.endsWith("==") shouldBe true
      result shouldNot include (nino)
      result shouldNot include (email)
    }

    "have a decode method that is the inverse of the encode method" in {
      val result = EmailVerificationParams.decode(params.encode)
      result.get.email shouldBe email
      result.get.nino shouldBe nino
    }

    "have a decode method that returns None when given a base64 string that does not encode a nino and email" in {
      val s = new String(Base64.getEncoder.encode("wibble".getBytes()), Charset.forName("UTF-8"))
      EmailVerificationParams.decode(s) shouldBe None
    }
  }
}
