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

package uk.gov.hmrc.helptosavefrontend.forms

import java.util.regex.Matcher

import cats.data.Validated.Valid
import cats.instances.int._
import cats.instances.string._
import cats.syntax.either._
import cats.syntax.eq._
import com.google.inject.Inject
import play.api.data.Forms.{optional, text}
import play.api.data.format.Formatter
import play.api.data.{Form, FormError}
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.forms.BankDetailsValidation.{ErrorMessages, StringOps}
import uk.gov.hmrc.helptosavefrontend.util.Validation.{ValidOrErrorStrings, invalid, validatedFromBoolean}

class BankDetailsValidation @Inject() (configuration: FrontendAppConfig) {

  import configuration.BankDetailsConfig._

  private val rollNoRegex: String => Matcher =
    (s"^([0-9a-zA-Z\\/.-]{$rollNumberMinLength,$rollNumberMaxLength})" + "$").r.pattern.matcher _

  val sortCodeFormatter: Formatter[SortCode] = new Formatter[SortCode] {

    val allowedSeparators = Set(' ', '-', '–', '−', '—')

    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], SortCode] = {
      val validation: ValidOrErrorStrings[SortCode] =
        data
          .get(key)
          .map(_.cleanupSpecialCharacters.removeAllSpaces)
          .fold(invalid[SortCode](ErrorMessages.sortCodeEmpty)) { s =>
            val p = s.filterNot(allowedSeparators.contains)
            if (p.length === sortCodeLength && p.forall(_.isDigit)) {
              SortCode(p.map(_.asDigit))
                .fold[ValidOrErrorStrings[SortCode]](invalid(ErrorMessages.sortCodeIncorrectFormat))(Valid(_))
            } else if (p.isEmpty) {
              invalid(ErrorMessages.sortCodeEmpty)
            } else {
              invalid(ErrorMessages.sortCodeIncorrectFormat)
            }
          }

      validation.toEither
        .leftMap(_.map(e => FormError(key, e)).toList)
    }

    override def unbind(key: String, value: SortCode): Map[String, String] =
      text.withPrefix(key).unbind(value.toString)
  }

  val accountNumberFormatter: Formatter[String] = new Formatter[String] {

    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], String] = {
      val validation: ValidOrErrorStrings[String] =
        data
          .get(key)
          .map(_.cleanupSpecialCharacters.removeAllSpaces)
          .fold(invalid[String](ErrorMessages.accountNumberEmpty)) { s =>
            validatedFromBoolean(s)(s => s.length === accountNumberLength && s.forall(_.isDigit), if (s.isEmpty) {
              ErrorMessages.accountNumberEmpty
            } else {
              ErrorMessages.accountNumberIncorrectFormat
            })
          }

      validation.toEither.leftMap(_.map(e => FormError(key, e)).toList)
    }

    override def unbind(key: String, value: String): Map[String, String] =
      text.withPrefix(key).unbind(value)
  }

  val rollNumberFormatter: Formatter[Option[String]] = new Formatter[Option[String]] {

    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[String]] = {
      val validation: ValidOrErrorStrings[Option[String]] =
        data
          .get(key)
          .filter(_.nonEmpty)
          .fold[ValidOrErrorStrings[Option[String]]](Valid(None)) { s =>
            if (rollNoRegex(s).matches()) {
              Valid(Some(s))
            } else if (s.length < rollNumberMinLength) {
              invalid(ErrorMessages.rollNumberTooShort)
            } else if (s.length > rollNumberMaxLength) {
              invalid(ErrorMessages.rollNumberTooLong)
            } else {
              invalid(ErrorMessages.rollNumberIncorrectFormat)
            }
          }
      validation.toEither.leftMap(_.map(e => FormError(key, e)).toList)
    }

    override def unbind(key: String, value: Option[String]): Map[String, String] =
      optional(text).withPrefix(key).unbind(value)
  }

  val accountNameFormatter: Formatter[String] = new Formatter[String] {

    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], String] = {
      val validation: ValidOrErrorStrings[String] =
        data
          .get(key)
          .map(_.cleanupSpecialCharacters.trim)
          .fold(invalid[String](ErrorMessages.accountNameEmpty)) { s =>
            if (s.isEmpty) {
              invalid(ErrorMessages.accountNameEmpty)
            } else if (s.length < accountNameMinLength) {
              invalid(ErrorMessages.accountNameTooShort)
            } else if (s.length > accountNameMaxLength) {
              invalid(ErrorMessages.accountNameTooLong)
            } else {
              Valid(s)
            }
          }

      validation.toEither.leftMap(_.map(e => FormError(key, e)).toList)
    }

    override def unbind(key: String, value: String): Map[String, String] =
      text.withPrefix(key).unbind(value)
  }

}

object BankDetailsValidation {

  object ErrorMessages {

    val sortCodeEmpty: String = "sort_code_empty"

    val sortCodeIncorrectFormat: String = "sort_code_incorrect_format"

    val accountNumberEmpty: String = "account_number_empty"

    val accountNumberIncorrectFormat: String = "account_number_incorrect_format"

    val rollNumberTooShort: String = "roll_number_too_short"

    val rollNumberTooLong: String = "roll_number_too_long"

    val rollNumberIncorrectFormat: String = "roll_number_incorrect_format"

    val accountNameEmpty: String = "account_name_empty"

    val accountNameTooShort: String = "account_name_too_short"

    val accountNameTooLong: String = "account_name_too_long"

    val sortCodeBackendInvalid: String = "check_your_sortcode_is_correct"

    val accountNumberBackendInvalid: String = "check_your_account_number_is_correct"
  }

  implicit class FormOps[A](val form: Form[A]) extends AnyVal {

    private def hasErrorMessage(key: String, message: String): Boolean =
      form.error(key).exists(_.message === message)

    def sortCodeEmpty(key: String): Boolean =
      hasErrorMessage(key, ErrorMessages.sortCodeEmpty)

    def sortCodeIncorrectFormat(key: String): Boolean =
      hasErrorMessage(key, ErrorMessages.sortCodeIncorrectFormat)

    def accountNumberEmpty(key: String): Boolean =
      hasErrorMessage(key, ErrorMessages.accountNumberEmpty)

    def accountNumberIncorrectFormat(key: String): Boolean =
      hasErrorMessage(key, ErrorMessages.accountNumberIncorrectFormat)

    def rollNumberTooShort(key: String): Boolean =
      hasErrorMessage(key, ErrorMessages.rollNumberTooShort)

    def rollNumberTooLong(key: String): Boolean =
      hasErrorMessage(key, ErrorMessages.rollNumberTooLong)

    def rollNumberIncorrectFormat(key: String): Boolean =
      hasErrorMessage(key, ErrorMessages.rollNumberIncorrectFormat)

    def accountNameEmpty(key: String): Boolean =
      hasErrorMessage(key, ErrorMessages.accountNameEmpty)

    def accountNameTooShort(key: String): Boolean =
      hasErrorMessage(key, ErrorMessages.accountNameTooShort)

    def accountNameTooLong(key: String): Boolean =
      hasErrorMessage(key, ErrorMessages.accountNameTooLong)

    def sortCodeBackendInvalid(key: String): Boolean =
      hasErrorMessage(key, ErrorMessages.sortCodeBackendInvalid)

    def accountNumberBackendInvalid(key: String): Boolean =
      hasErrorMessage(key, ErrorMessages.accountNumberBackendInvalid)

  }

  implicit class StringOps(val s: String) {
    def removeAllSpaces: String = s.replaceAll(" ", "")

    def cleanupSpecialCharacters: String = s.replaceAll("\t|\n|\r", " ").trim.replaceAll("\\s{2,}", " ")

  }

}
