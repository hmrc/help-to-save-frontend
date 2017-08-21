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

case class EmailVerificationParams(nino: String, email: String) {
  def encode(): String = {
    val input = nino + ":" + email
    DataEncrypter.encrypt(input)
  }
}

object EmailVerificationParams {
  def decode(base64: String): Option[EmailVerificationParams] = {
    DataEncrypter.decrypt(base64) match {
      case Left(_) ⇒ None
      case Right(decrypted) ⇒
        val params = decrypted.split(":")
        if (params.length == 2) Some(EmailVerificationParams(params(0), params(1))) else None
    }
  }
}
