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

package uk.gov.hmrc.helptosavefrontend

import cats.data.EitherT

import java.net.{URLDecoder, URLEncoder}
import java.util.Base64
import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.matching.Regex

package object util {

  type NINO = String

  type UserDetailsURI = String

  type Email = String

  type Result[A] = EitherT[Future, String, A]

  implicit def toFuture[A](a: A): Future[A] = Future.successful(a)

  def base64Encode(input: String): Array[Byte] = Base64.getEncoder.encode(input.getBytes)

  def base64Decode(input: String): Array[Byte] = Base64.getDecoder.decode(input)

  def urlEncode(url: String): String = URLEncoder.encode(url, "UTF-8")

  def urlDecode(url: String): String = URLDecoder.decode(url, "UTF-8")

  val ninoRegex: Regex = """[A-Za-z]{2}[0-9]{6}[A-Za-z]{1}""".r

  def maskNino(original: String): String =
    Option(original) match {
      case Some(text) => ninoRegex.replaceAllIn(text, "<NINO>")
      case None       => original
    }

}
