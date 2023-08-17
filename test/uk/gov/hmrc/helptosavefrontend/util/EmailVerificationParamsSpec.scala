/*
 * Copyright 2023 HM Revenue & Customs
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

import scala.util.{Failure, Success, Try}

class EmailVerificationParamsSpec extends UnitSpec {

  val nino: NINO = "AE1234XXX"
  val email: Email = "email@gmail.com"
  val params: EmailVerificationParams = EmailVerificationParams(nino, email)

  def successfulCrypto(): Crypto = new Crypto {
    override def encrypt(s: String): String = s

    override def decrypt(s: String): Try[String] = Success(s)
  }

  "EmailVerificationParams" must {

    "have a decode method that is the inverse of the encode method" in {
      implicit val crypto: Crypto = successfulCrypto()
      val result = EmailVerificationParams
        .decode(params.encode)
        .getOrElse(fail("Could not decode email verification params"))
      result.email shouldBe email
      result.nino shouldBe nino
    }

    "have a decode method that returns a Failure" when {
      "given a base64 string that does not encode a nino and email" in {
        implicit val crypto: Crypto = new Crypto {
          override def encrypt(s: String): String = s

          override def decrypt(s: String): Try[String] = Failure(new Exception)
        }

        EmailVerificationParams.decode(params.encode).isFailure shouldBe true
      }

      "the parameters do not contain a hash symbol" in {
        implicit val crypto: Crypto = successfulCrypto()

        val s = new String(Base64.getEncoder.encode("wibble".getBytes()), Charset.forName("UTF-8"))
        EmailVerificationParams.decode(s).isFailure shouldBe true
      }

      "given a string that is not base64 encoded" in {
        implicit val crypto: Crypto = successfulCrypto()

        EmailVerificationParams.decode("###123").isFailure shouldBe true
      }
    }

  }

}
