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

package uk.gov.hmrc.helptosavefrontend.views.helpers

import play.api.data.{Form, FormError}
import play.api.i18n.Messages
import play.twirl.api._
import uk.gov.hmrc.govukfrontend.views.Aliases.Text
import uk.gov.hmrc.govukfrontend.views.viewmodels.errormessage.ErrorMessage
import uk.gov.hmrc.govukfrontend.views.html.components._
import uk.gov.hmrc.helptosavefrontend.views.ViewHelpers
import uk.gov.hmrc.helptosavefrontend.forms.EmailValidation.FormOps
import javax.inject.Inject

class FormErrorMessage @Inject() (ui: ViewHelpers) {

  def errorText(formName: String, e: FormError)(implicit messages: Messages): String =
    messages(s"$formName.${e.key}.${e.message}", e.args: _*)

  def govukErrorText(formName: String, e: FormError)(implicit messages: Messages): Text = Text(errorText(formName, e))

  def errorSummary[A](
    formName: String,
    form: Form[A],
    customErrorFunction: Option[(Form[A], String) => Option[String]] = None
  )(implicit messages: Messages): Option[HtmlFormat.Appendable] =
    if (form.errors.nonEmpty) {
      Some(
        ui.govukErrorSummary(
          ErrorSummary(
            errorList = form.errors.map(
              e =>
                ErrorLink(
                  href = Some(s"#${e.key}"),
                  content = customErrorFunction match {
                    case None    => Text(errorText(formName, e))
                    case Some(f) => Text(f.apply(form, e.key).getOrElse(errorText(formName, e)))
                  }
                )
            ),
            title = Text(messages("hts.global.error-summary.title"))
          )
        )
      )
    } else None
  def formErrorMessage(formName: String, form: Form[_], key: String)(
    implicit messages: Messages
  ): Option[ErrorMessage] =
    form
      .error(key)
      .map(
        e =>
          ErrorMessage(
            content = Text(errorText(formName, e)),
            visuallyHiddenText = Some(messages("hts.global.error.prefix"))
          )
      )

  def emailErrorTypesToString(form: Form[_], key: String)(implicit messages: play.api.i18n.Messages): Option[String] = {
    val messagesKey: String = if (form.emailIsBlank(key)) {
      "hts.email.error.blank"
    } else if (form.emailLocalLengthTooShort(key)) {
      "hts.email.error.local-too-short"
    } else if (form.emailDomainLengthTooShort(key)) {
      "hts.email.error.domain-too-short"
    } else if (form.emailLocalLengthTooLong(key)) {
      "hts.email.error.local-too-long"
    } else if (form.emailDomainLengthTooLong(key)) {
      "hts.email.error.domain-too-long"
    } else if (form.emailTotalLengthTooLong(key)) {
      "hts.email.error.total-too-long"
    } else if (form.emailHasNoAtSymbol(key)) {
      "hts.email.error.no-at-symbol"
    } else if (form.emailHasNoDotSymbol(key)) {
      "hts.email.error.no-dot-symbol"
    } else if (form.emailHasNoTextAfterDotSymbol(key)) {
      "hts.email.error.no-text-after-dot-symbol"
    } else if (form.emailHasNoTextAfterAtSymbolButBeforeDot(key)) {
      "hts.email.error.no-text-after-at-symbol-but-before-dot"
    } else {
      ""
    }

    if (!messagesKey.isEmpty) {
      Some(messages(messagesKey))
    } else {
      None
    }
  }
}
