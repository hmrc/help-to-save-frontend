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

package src.test.scala.hts.utils

import cats.data._
import uk.gov.hmrc.domain.Generator

import scala.util.matching.Regex

import scala.annotation.tailrec
import cats.syntax.cartesian._

trait NINOGenerator {

  val ninoRegexOriginal = ("""^(([A-CEGHJ-PR-TW-Z][A-CEGHJ-NPR-TW-Z])([0-9]{2})([0-9]{2})""" +
    """([0-9]{2})([A-D]{1})|((XX)(99)(99)(99)(X)))$""").r

  def generateNINO: String = {
    val generator = new Generator()
    val nino = generator.nextNino.toString()
    println("########### nino generated: " + nino)
    println("######### nino validated? " + ninoValidation(nino))
    nino
  }

  def generateEligibleNINO: String = {
    val nino = generateNINO
    println("########### eligible nino used: AE" + nino.slice(2, nino.length))
    val nino2 = "AE" + nino.slice(2, nino.length)
    println("######## nino2: " + nino2)
    println("######### nino validated? " + ninoValidation(nino2))
    "AE" + nino.slice(2, nino.length)
  }

  def generateIllegibleNINO: String = {
    val nino = generateNINO
    println("########### illegible nino used: NA" + nino.slice(2, nino.length))
    val nino2 = "AE" + nino.slice(2, nino.length)
    println("######### nino validated? " + ninoValidation(nino2))
    "NA" + nino.slice(2, nino.length)
  }

  def validatedFromBoolean[A](a: A)(isValid: A ⇒ Boolean, ifFalse: ⇒ String): ValidatedNel[String, A] =
    if (isValid(a)) Validated.Valid(a) else Validated.Invalid(NonEmptyList.of(ifFalse))

  def regexValidation(s: String)(regex: Regex, ifInvalid: ⇒ String): ValidatedNel[String, String] =
    validatedFromBoolean(s)(regex.pattern.matcher(_).matches(), ifInvalid)

  def ninoValidation(nino: String): ValidatedNel[String, String] = {
    val lengthCheck =
      validatedFromBoolean(nino)(_.length <= 9, "NINO was longer thn 9 characters")

    val regexCheck =
      regexValidation(nino)(ninoRegexOriginal, "Invalid NINO format")

    (lengthCheck |@| regexCheck).tupled.map(_ ⇒ nino)
  }

}
