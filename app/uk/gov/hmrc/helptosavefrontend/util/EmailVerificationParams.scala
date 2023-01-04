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

import scala.util.Try

case class EmailVerificationParams(nino: String, email: String) {
  def encode()(implicit crypto: Crypto): String = {
    val input = nino + "#" + email
    // I now believe base64 has been used here to circumvent a ZAP alert for a possible padding oracle attack,
    // but AES GCM is not vulnerable, because it is authenticated and does not require padding
    new String(base64Encode(crypto.encrypt(input)))
  }
}

object EmailVerificationParams {

  def decode(base64: String)(implicit crypto: Crypto): Try[EmailVerificationParams] =
    for {
      decoded ← Try(new String(base64Decode(base64)))
      decrypted ← crypto.decrypt(decoded)
      (nino, email) ← Try {
                       val nino = decrypted.substring(0, decrypted.indexOf('#'))
                       val email = decrypted.substring(decrypted.indexOf('#') + 1)
                       nino → email
                     }
    } yield EmailVerificationParams(nino, email)
}
