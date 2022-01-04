/*
 * Copyright 2022 HM Revenue & Customs
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
import cats.instances.string._
import cats.syntax.either._
import cats.syntax.eq._
import com.google.inject.Inject
import play.api.data.Forms.text
import play.api.data.format.Formatter
import play.api.data.{Form, FormError}
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.forms.ReminderFrequencyValidation.{ErrorMessages, StringOps}
import uk.gov.hmrc.helptosavefrontend.util.Validation.{ValidOrErrorStrings, invalid}

class ReminderFrequencyValidation @Inject() (configuration: FrontendAppConfig) {

  val reminderFrequencyFormatter: Formatter[String] = new Formatter[String] {

    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], String] = {
      val validation: ValidOrErrorStrings[String] = {
        data
          .get(key)
          .map(_.cleanupSpecialCharacters.trim)
          .fold(invalid[String](ErrorMessages.reminderFrequencyEmpty)) { s ⇒
            if (s.isEmpty) {
              invalid(ErrorMessages.reminderFrequencyEmpty)
            } else {
              Valid(s)
            }
          }
      }

      validation.toEither.leftMap(_.map(e ⇒ FormError(key, e)).toList)
    }

    override def unbind(key: String, value: String): Map[String, String] =
      text.withPrefix(key).unbind(value)
  }
}

object ReminderFrequencyValidation {

  object ErrorMessages {

    val reminderFrequencyEmpty: String = "reminder_frequency_empty"

  }

  implicit class FormOps[A](val form: Form[A]) extends AnyVal {

    private def hasErrorMessage(key: String, message: String): Boolean =
      form.error(key).exists(_.message === message)

    def frequencyChoiceEmpty(key: String): Boolean =
      hasErrorMessage(key, ErrorMessages.reminderFrequencyEmpty)

  }

  implicit class StringOps(val s: String) {

    def removeAllSpaces: String = s.replaceAll(" ", "")

    def cleanupSpecialCharacters: String = s.replaceAll("\t|\n|\r", " ").trim.replaceAll("\\s{2,}", " ")

  }

}
