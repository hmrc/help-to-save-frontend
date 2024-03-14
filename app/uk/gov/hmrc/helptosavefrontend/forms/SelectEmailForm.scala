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
          // if "Change" make sure that we have a new email
          case Some("Change") => emailValidation.emailFormatter.bind(key, data).map(Some(_))

          // the value for "email" should be "UserInfo" or "Newer" or "Change" - this will get picked up in the mapping in
          // the form below.
          // if the value is "UserInfo" or "Newer" ignore any new email that has been entered
          case _ => Right(None)
        }

      override def unbind(key: String, value: Option[String]): Map[String, String] =
        optional(email).withPrefix(key).unbind(value)
    }

    Form(
      mapping(
        "email"     -> text.verifying(l => l === "UserInfo" || l === "Newer" || l === "Change"),
        "new-email" -> of(emailFormatter)
      )(SelectEmail.apply)(SelectEmail.unapply)
    )
  }
}

case class SelectEmail(checked: String, newestEmail: Option[String]) {
  def userInfoIfChecked(userInfoEmail: String, newerEmail: Option[String]): String =
    if (checked === "UserInfo") {
      userInfoEmail
    } else {
      newerEmail.getOrElse("")
    }
}
