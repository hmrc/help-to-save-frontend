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
import javax.crypto.{Cipher, SecretKey, SecretKeyFactory}
import javax.crypto.spec.DESKeySpec

import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig._

import scala.util.control.NonFatal

case class EmailVerificationParams(nino: String, email: String) {
  def encode(): String = {
    val input = nino + "§" + email
    DataEncrypter.encrypt(input)
  }
}

object EmailVerificationParams {
  def decode(base64: String): Option[EmailVerificationParams] = {
    DataEncrypter.decrypt(base64) match {
      case Left(_) ⇒ None
      case Right(decrypted) ⇒
        val params = decrypted.split("§")
        if (params.length == 2) Some(EmailVerificationParams(params(0), params(1))) else None
    }
  }
}

object DataEncrypter {

  private val key: SecretKey = {
    val keySpec: DESKeySpec = new DESKeySpec(mongoEncSeed.getBytes("UTF-8"))
    val keyFactory: SecretKeyFactory = SecretKeyFactory.getInstance("DES")
    keyFactory.generateSecret(keySpec)
  }

  private val cipher: Cipher = Cipher.getInstance("DES")

  def encrypt(data: String): String = {
    cipher.init(Cipher.ENCRYPT_MODE, key)
    base64Encode(cipher.doFinal(data.getBytes("UTF-8")))
  }

  def decrypt(data: String): Either[String,String] = try {
    cipher.init(Cipher.DECRYPT_MODE, key)
    Right(new String(cipher.doFinal(base64Decode(data)), "UTF-8"))
  } catch {
    case NonFatal(e) ⇒ Left(e.getMessage)
  }

  private def base64Encode(bytes: Array[Byte]) = Base64.getUrlEncoder.encodeToString(bytes)

  private def base64Decode(property: String) = Base64.getUrlDecoder.decode(property)
}
