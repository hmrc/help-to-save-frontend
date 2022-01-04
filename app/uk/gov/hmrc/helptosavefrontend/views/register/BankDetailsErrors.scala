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
      Some(messages("hts.sort-code.empty"))
    } else if (has(_.sortCodeIncorrectFormat)) {
      Some(messages("hts.sort-code.incorrect-format", appConfig.BankDetailsConfig.sortCodeLength))
    } else if (has(_.accountNumberEmpty)) {
      Some(messages("hts.account-number.empty"))
    } else if (has(_.accountNumberIncorrectFormat)) {
      Some(messages("hts.account-number.incorrect-format", appConfig.BankDetailsConfig.accountNumberLength))
    } else if (has(_.rollNumberTooShort)) {
      Some(messages("hts.roll-number.too-short", appConfig.BankDetailsConfig.rollNumberMinLength))
    } else if (has(_.rollNumberTooLong)) {
      Some(messages("hts.roll-number.too-long", appConfig.BankDetailsConfig.rollNumberMaxLength))
    } else if (has(_.rollNumberIncorrectFormat)) {
      Some(messages("hts.roll-number.invalid"))
    } else if (has(_.accountNameEmpty)) {
      Some(messages("hts.account-name.empty"))
    } else if (has(_.accountNameTooShort)) {
      Some(messages("hts.account-name.too-short", appConfig.BankDetailsConfig.accountNameMinLength))
    } else if (has(_.accountNameTooLong)) {
      Some(messages("hts.account-name.too-long", appConfig.BankDetailsConfig.accountNameMaxLength))
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
