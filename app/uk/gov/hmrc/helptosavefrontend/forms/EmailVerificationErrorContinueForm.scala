/*
 * Copyright 2020 HM Revenue & Customs
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

import play.api.data.Forms.{mapping, of}
import play.api.data.format.Formatter
import play.api.data.{Form, FormError}

object EmailVerificationErrorContinueForm {

  // the default boolean format returns a field with false if the field doesn't exist - the
  // formatter below fails if the field does not exist
  val booleanFormat: Formatter[Boolean] = new Formatter[Boolean] {
    def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Boolean] = {
      data.get(key).fold[Either[Seq[FormError], Boolean]](
        Left(Seq(FormError(key, "error.boolean", Nil)))){
          case "true"  ⇒ Right(true)
          case "false" ⇒ Right(false)
          case _       ⇒ Left(Seq(FormError(key, "error.boolean", Nil)))
        }
    }

    def unbind(key: String, value: Boolean) = Map(key -> value.toString)
  }

  val continueForm: Form[Continue] = Form(
    mapping("radio-inline-group" → of(booleanFormat))(Continue.apply)(Continue.unapply)
  )
}

case class Continue(value: Boolean)
