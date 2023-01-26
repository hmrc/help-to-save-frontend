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

package uk.gov.hmrc.helptosavefrontend.views.register

import play.api.data.Form
import play.api.i18n.Messages
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.forms.BankDetailsValidation.FormOps

object BankDetailsErrors {

  def getErrorMessage(
    form: Form[_], // scalastyle:ignore cyclomatic.complexity
    key: String
  )(implicit messages: Messages, appConfig: FrontendAppConfig): Option[String] = {
    def has(f: Form[_] ⇒ String ⇒ Boolean): Boolean = f(form)(key)

    if (has(_.sortCodeEmpty)) {
      Some(messages("bank-details.sortCode.error.required"))
    } else if (has(_.sortCodeIncorrectFormat)) {
      Some(messages("bank-details.sortCode.error.pattern", appConfig.BankDetailsConfig.sortCodeLength))
    } else if (form.error(key).nonEmpty && has(_.accountNumberEmpty)) {
      Some(messages("bank-details.accountNumber.error.required"))
    } else if (form.error(key).nonEmpty && has(_.accountNumberIncorrectFormat)) {
      Some(messages("bank-details.accountNumber.error.pattern", appConfig.BankDetailsConfig.accountNumberLength))
    } else if (form.error(key).nonEmpty && has(_.rollNumberTooShort)) {
      Some(messages("bank-details.rollNumber.error.tooShort", appConfig.BankDetailsConfig.rollNumberMinLength))
    } else if (has(_.rollNumberTooLong)) {
      Some(messages("bank-details.rollNumber.error.tooLong", appConfig.BankDetailsConfig.rollNumberMaxLength))
    } else if (form.error(key).nonEmpty && has(_.rollNumberIncorrectFormat)) {
      Some(messages("bank-details.rollNumber.error.invalid"))
    } else if (has(_.accountNameEmpty)) {
      Some(messages("bank-details.accountName.error.required"))
    } else if (has(_.accountNameTooShort)) {
      Some(messages("bank-details.accountName.error.tooShort", appConfig.BankDetailsConfig.accountNameMinLength))
    } else if (has(_.accountNameTooLong)) {
      Some(messages("bank-details.accountName.error.tooLong", appConfig.BankDetailsConfig.accountNameMaxLength))
    } else if (form.sortCodeBackendInvalid(key)) {
      Some(messages("bank-details.sortCode.error.invalid"))
    } else if (form.accountNumberBackendInvalid(key)) {
      Some(messages("bank-details.accountNumber.error.invalid"))
    } else {
      None
    }
  }

  def getBankDetailsBackendErrorMessage(
    form: Form[_],
    key: String
  )(implicit messages: Messages): Option[String] =
    if (form.sortCodeBackendInvalid(key)) {
      Some(messages("hts.bank_details.check_your_sortcode_is_correct"))
    } else if (form.accountNumberBackendInvalid(key)) {
      Some(messages("hts.bank_details.check_your_account_number_is_correct"))
    } else {
      None
    }
}
