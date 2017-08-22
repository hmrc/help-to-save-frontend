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

import org.scalatest.prop.GeneratorDrivenPropertyChecks
import uk.gov.hmrc.helptosavefrontend.TestSupport

class EmailVerificationParamsSpec extends TestSupport with GeneratorDrivenPropertyChecks {

  val nino = "AE1234XXX"
  val email = "email@gmail.com"
  val params = EmailVerificationParams(nino, email)

  "EmailVerificationParams" must {
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

  "The DataEncrypter" must {

    "correctly encrypt and decrypt the data given" in {

      val original = "user+mailbox/department=shipping@example.com"

      val encoded = DataEncrypter.encrypt(original)

      encoded should not be original

      val decoded = DataEncrypter.decrypt(encoded)

      decoded should be(Right(original))
    }

    "correctly encrypt and decrypt the data when there are special characters" in {

      val original = "Dörte@Sören!#$%&'*+-/=?^_`उपयोगकर्ता@उदाहरण.कॉम.{|}~@example.com"

      val encoded = DataEncrypter.encrypt(original)

      encoded should not be original

      val decoded = DataEncrypter.decrypt(encoded)

      decoded should be(Right(original))
    }

    "return an error when there are errors decrypting" in {
      forAll{ s: String ⇒
        whenever(s.nonEmpty){
          DataEncrypter.decrypt(s).isLeft shouldBe true
        }
      }
    }

  }
}
