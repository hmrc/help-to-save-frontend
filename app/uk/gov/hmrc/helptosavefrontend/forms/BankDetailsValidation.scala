/*
 * Copyright 2018 HM Revenue & Customs
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

import cats.data.Validated.Valid
import cats.instances.int._
import cats.instances.string._
import cats.syntax.either._
import cats.syntax.eq._
import com.google.inject.Inject
import play.api.data.{Form, FormError}
import play.api.data.Forms.{optional, text}
import play.api.data.format.Formatter
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.util.Validation.{ValidOrErrorStrings, invalid, validatedFromBoolean}
import uk.gov.hmrc.helptosavefrontend.forms.BankDetailsValidation.{ErrorMessages, StringOps}

class BankDetailsValidation @Inject() (configuration: FrontendAppConfig) {

  import configuration.BankDetailsValidation._

  val sortCodeFormatter: Formatter[String] = new Formatter[String] {

    val allowedSeparators = Set(' ', '-', '_')

    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], String] = {
      val validation: ValidOrErrorStrings[String] =
        data.get(key)
          .map(_.cleanupSpecialCharacters.removeAllSpaces)
          .fold(invalid[String](ErrorMessages.sortCodeIncorrectFormat)){ s ⇒
            validatedFromBoolean(s.filterNot(allowedSeparators.contains))(
              s ⇒ s.length === sortCodeLength && s.forall(_.isDigit), ErrorMessages.sortCodeIncorrectFormat)
          }

      validation.toEither.leftMap(_.map(e ⇒ FormError(key, e)).toList)
    }

    override def unbind(key: String, value: String): Map[String, String] =
      text.withPrefix(key).unbind(value)
  }

  val accountNumberFormatter: Formatter[String] = new Formatter[String] {

    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], String] = {
      val validation: ValidOrErrorStrings[String] =
        data.get(key)
          .map(_.cleanupSpecialCharacters.removeAllSpaces)
          .fold(invalid[String](ErrorMessages.accountNumberIncorrectFormat)){ s ⇒
            validatedFromBoolean(s)(s ⇒ s.length === accountNumberLength && s.forall(_.isDigit), ErrorMessages.accountNumberIncorrectFormat)
          }

      validation.toEither.leftMap(_.map(e ⇒ FormError(key, e)).toList)
    }

    override def unbind(key: String, value: String): Map[String, String] =
      text.withPrefix(key).unbind(value)
  }

  val rollNumberFormatter: Formatter[Option[String]] = new Formatter[Option[String]] {

    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[String]] = {
      val validation: ValidOrErrorStrings[Option[String]] =
        data.get(key)
          .map(_.cleanupSpecialCharacters.removeAllSpaces)
          .filter(_.nonEmpty)
          .fold[ValidOrErrorStrings[Option[String]]](Valid(None)){ s ⇒
            if (s.length < rollNumberMinLength) {
              invalid(ErrorMessages.rollNumberTooShort)
            } else if (s.length > rollNumberMaxLength) {
              invalid(ErrorMessages.rollNumberTooLong)
            } else {
              Valid(Some(s))
            }
          }

      validation.toEither.leftMap(_.map(e ⇒ FormError(key, e)).toList)
    }

    override def unbind(key: String, value: Option[String]): Map[String, String] =
      optional(text).withPrefix(key).unbind(value)
  }

  val accountNameFormatter: Formatter[String] = new Formatter[String] {

    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], String] = {
      val validation: ValidOrErrorStrings[String] =
        data.get(key)
          .map(_.cleanupSpecialCharacters.trim)
          .fold(invalid[String](ErrorMessages.accountNameTooShort)){ s ⇒
            if (s.length < accountNameMinLength) {
              invalid(ErrorMessages.accountNameTooShort)
            } else if (s.length > accountNameMaxLength) {
              invalid(ErrorMessages.accountNameTooLong)
            } else {
              Valid(s)
            }
          }

      validation.toEither.leftMap(_.map(e ⇒ FormError(key, e)).toList)
    }

    override def unbind(key: String, value: String): Map[String, String] =
      text.withPrefix(key).unbind(value)
  }

}

object BankDetailsValidation {

  private[forms] object ErrorMessages {

    val sortCodeIncorrectFormat = "sort-code-incorrect-format"

    val accountNumberIncorrectFormat = "account-number-incorrect-format"

    val rollNumberTooShort = "roll-number-too-short"

    val rollNumberTooLong = "roll-number-too-long"

    val accountNameTooShort = "account-name-too-short"

    val accountNameTooLong = "account-name-too-long"

  }

  implicit class FormOps[A](val form: Form[A]) extends AnyVal {

    private def hasErrorMessage(key: String, message: String): Boolean =
      form.error(key).exists(_.message === message)

    def sortCodeIncorrectFormat(key: String): Boolean =
      hasErrorMessage(key, ErrorMessages.sortCodeIncorrectFormat)

    def accountNumberIncorrectFormat(key: String): Boolean =
      hasErrorMessage(key, ErrorMessages.accountNumberIncorrectFormat)

    def rollNumberTooShort(key: String): Boolean =
      hasErrorMessage(key, ErrorMessages.rollNumberTooShort)

    def rollNumberTooLong(key: String): Boolean =
      hasErrorMessage(key, ErrorMessages.rollNumberTooLong)

    def accountNameTooShort(key: String): Boolean =
      hasErrorMessage(key, ErrorMessages.accountNameTooShort)

    def accountNameTooLong(key: String): Boolean =
      hasErrorMessage(key, ErrorMessages.accountNameTooLong)

  }

  implicit class StringOps(val s: String) {
    def removeAllSpaces: String = s.replaceAll(" ", "")

    def cleanupSpecialCharacters: String = s.replaceAll("\t|\n|\r", " ").trim.replaceAll("\\s{2,}", " ")

  }

}
