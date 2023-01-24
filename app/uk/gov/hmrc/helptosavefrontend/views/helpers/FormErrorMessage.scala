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

import play.api.data.Form
import play.api.i18n.Messages
import play.twirl.api._
import uk.gov.hmrc.govukfrontend.views.Aliases.Text
import uk.gov.hmrc.govukfrontend.views.viewmodels.errormessage.ErrorMessage
import uk.gov.hmrc.govukfrontend.views.html.components._
import uk.gov.hmrc.helptosavefrontend.views.ViewHelpers
import javax.inject.Inject

class FormErrorMessage @Inject()(ui: ViewHelpers) {

  def errorSummary(formName: String, form: Form[_])(
      implicit messages: Messages): Option[HtmlFormat.Appendable] =
    if(form.errors.nonEmpty) {
      Some(ui.govukErrorSummary(ErrorSummary(errorList = form.errors.map(e =>
        ErrorLink(
          href = Some(s"#${e.key}"),
          content = Text(s"${messages(s"${formName}.${e.key}.${e.message}")}")
        )
      ),
        title = Text(messages("hts.global.error-summary.title")))))
    } else None
  def formErrorMessage(formName: String, form: Form[_], key: String)(
    implicit messages: Messages): Option[ErrorMessage] =
    form
      .error(key)
      .map(
        e =>
          ErrorMessage(
            content = Text(messages(s"${formName}.${e.key}.${e.message}", e.args: _*)),
            visuallyHiddenText = Some(messages("generic.errorPrefix"))
        ))
}
