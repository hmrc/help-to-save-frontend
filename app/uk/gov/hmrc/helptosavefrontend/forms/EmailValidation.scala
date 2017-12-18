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

package uk.gov.hmrc.helptosavefrontend.forms

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.instances.char._
import cats.instances.string._
import cats.syntax.cartesian._
import cats.syntax.either._
import cats.syntax.eq._
import com.google.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.data.Forms.text
import play.api.data.{Form, FormError}
import play.api.data.format.Formatter
import uk.gov.hmrc.helptosavefrontend.forms.EmailValidation.ErrorMessages

import scala.annotation.tailrec

@Singleton
class EmailValidation @Inject() (configuration: Configuration) {

  private val emailMaxTotalLength: Int = configuration.underlying.getInt("email-validation.max-total-length")

  private val emailMaxLocalLength: Int = configuration.underlying.getInt("email-validation.max-local-length")

  private val emailMaxDomainLength: Int = configuration.underlying.getInt("email-validation.max-domain-length")

  val emailFormatter: Formatter[String] = new Formatter[String] {

    private def invalid[A](message: String): ValidatedNel[String, A] = Invalid(NonEmptyList[String](message, Nil))

    private def validatedFromBoolean[A](a: A)(predicate: A ⇒ Boolean, ifFalse: ⇒ String): ValidatedNel[String, A] =
      if (predicate(a)) Valid(a) else invalid(ifFalse)

    private def charactersBeforeAndAfterChar(c: Char)(s: String): Option[(Int, Int)] = {
        @tailrec
        def loop(chars: List[Char], count: Int): Option[(Int, Int)] = chars match {
          case Nil    ⇒ None
          case h :: t ⇒ if (h === c) { Some(count → t.length) } else { loop(t, count + 1) }
        }

      loop(s.toList, 0)
    }

    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], String] = {
      val validation: Validated[NonEmptyList[String], String] =
        data.get(key).fold(invalid[String](ErrorMessages.blankEmailAddress)) {
          s ⇒
            val trimmed = s.trim
            val localAndDomainLength = charactersBeforeAndAfterChar('@')(trimmed)
            val notBlankCheck = validatedFromBoolean(trimmed)(_.nonEmpty, ErrorMessages.blankEmailAddress)
            val totalLengthCheck = validatedFromBoolean(trimmed)(_.length <= emailMaxTotalLength, ErrorMessages.totalTooLong)
            val hasAtSymbolCheck = validatedFromBoolean(trimmed)(_.contains('@'), ErrorMessages.noAtSymbol)

            val localLengthCheck = validatedFromBoolean(localAndDomainLength)(_.forall(_._1 <= emailMaxLocalLength), ErrorMessages.localTooLong)
            val domainLengthCheck = validatedFromBoolean(localAndDomainLength)(_.forall(_._2 <= emailMaxDomainLength), ErrorMessages.domainTooLong)

            val localBlankCheck = validatedFromBoolean(localAndDomainLength)(_.forall(_._1 > 0), ErrorMessages.localTooShort)
            val domainBlankCheck = validatedFromBoolean(localAndDomainLength)(_.forall(_._2 > 0), ErrorMessages.domainTooShort)

            (notBlankCheck |@| totalLengthCheck |@| hasAtSymbolCheck |@|
              localLengthCheck |@| domainLengthCheck |@| localBlankCheck |@| domainBlankCheck)
              .map{ case _ ⇒ s }

        }

      validation.toEither.leftMap(_.map(e ⇒ FormError(key, e)).toList)
    }

    override def unbind(key: String, value: String): Map[String, String] =
      text.withPrefix(key).unbind(value)
  }

}

object EmailValidation {

  private[forms] object ErrorMessages {

    val totalTooLong: String = "total_too_long"

    val localTooLong: String = "local_too_long"

    val domainTooLong: String = "domain_too_long"

    val localTooShort: String = "local_too_short"

    val domainTooShort: String = "domain_too_short"

    val noAtSymbol: String = "no_@_symbol"

    val blankEmailAddress = "blank_email_address"
  }

  implicit class FormOps[A](val form: Form[A]) extends AnyVal {

    private def hasErrorMessage(key: String, message: String): Boolean =
      form.error(key).exists(_.message === message)

    def emailTotalLengthTooLong(key: String): Boolean =
      hasErrorMessage(key, ErrorMessages.totalTooLong)

    def emailLocalLengthTooLong(key: String): Boolean =
      hasErrorMessage(key, ErrorMessages.localTooLong)

    def emailLocalLengthTooShort(key: String): Boolean =
      hasErrorMessage(key, ErrorMessages.localTooShort)

    def emailDomainLengthTooLong(key: String): Boolean =
      hasErrorMessage(key, ErrorMessages.domainTooLong)

    def emailDomainLengthTooShort(key: String): Boolean =
      hasErrorMessage(key, ErrorMessages.domainTooShort)

    def emailHasNoAtSymbol(key: String): Boolean =
      hasErrorMessage(key, ErrorMessages.noAtSymbol)

    def emailIsBlank(key: String): Boolean =
      hasErrorMessage(key, ErrorMessages.blankEmailAddress)

  }

}
