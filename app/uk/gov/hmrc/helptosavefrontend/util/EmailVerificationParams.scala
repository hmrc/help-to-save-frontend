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

import java.util.Base64

import cats.instances.int._
import cats.syntax.eq._

import scala.util.{Failure, Success, Try}

case class EmailVerificationParams(nino: String, email: String) {
  private val encoder = Base64.getEncoder

  def encode()(implicit crypto: Crypto): String = {
    val input = nino + "#" + email
    new String(encoder.encode(crypto.encrypt(input).getBytes))
  }
}

object EmailVerificationParams {

  private val decoder = Base64.getDecoder

  def decode(base64: String)(implicit crypto: Crypto): Option[EmailVerificationParams] = {
    Try(new String(decoder.decode(base64))).flatMap(crypto.decrypt) match {
      case Failure(_) ⇒ None
      case Success(decrypted) ⇒
        val nino = decrypted.substring(0, decrypted.indexOf('#'))
        val email = decrypted.substring(decrypted.indexOf('#') + 1)
        Some(EmailVerificationParams(nino, email))
    }
  }
}
