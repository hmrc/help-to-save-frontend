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

import cats.instances.string._
import cats.syntax.eq._
import play.api.data.Forms._
import play.api.data._
import play.api.data.format.Formatter

object SelectEmailForm {

  def selectEmailForm(implicit emailValidation: EmailValidation): Form[SelectEmail] = {
    val emailFormatter = new Formatter[Option[String]] {
      override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[String]] =
        data.get("email") match {
          // if "No" make sure that we have a new email
          case Some("No") ⇒ emailValidation.emailFormatter.bind(key, data).map(Some(_))

          // the value for "email" should be "Yes" or "No" - this will get picked up in the mapping in the form below.
          // if the value is "Yes" ignore any new email that has been entered
          case _ ⇒ Right(None)
        }

      override def unbind(key: String, value: Option[String]): Map[String, String] =
        optional(email).withPrefix(key).unbind(value)
    }

    Form(
      mapping(
        "email" → text.verifying(l ⇒ l === "Yes" || l === "No"),
        "new-email" → of(emailFormatter)
      )(SelectEmail.apply)(SelectEmail.unapply)
    )
  }
}

case class SelectEmail(checked: String, newEmail: Option[String])
