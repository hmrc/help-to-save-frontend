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

case class EmailVerificationParams(nino: String, email: String) {
  def encode(): String = {
    val input = nino + ":" + email
    new String(Base64.getEncoder.encode(input.getBytes()), Charset.forName("UTF-8"))
  }
}

object EmailVerificationParams {
  def decode(base64: String): Option[EmailVerificationParams] = {
    val input = new String(Base64.getDecoder.decode(base64), Charset.forName("UTF-8")).split(":")
    if (input.length == 2) Some(EmailVerificationParams(input(0), input(1))) else None
  }
}
