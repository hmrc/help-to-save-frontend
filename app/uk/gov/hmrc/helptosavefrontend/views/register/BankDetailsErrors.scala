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

package uk.gov.hmrc.helptosavefrontend.views.register

import play.api.data.Form
import play.api.i18n.Messages
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.forms.BankDetailsValidation.FormOps

object BankDetailsErrors {

  def getErrorMessage(form: Form[_], key: String)(implicit messages: Messages, appConfig: FrontendAppConfig): Option[String] = {
    if (form.sortCodeIncorrectFormat(key)) {
      Some(messages("hts.sort-code.incorrect-format", appConfig.BankDetailsConfig.sortCodeLength))
    } else if (form.accountNumberIncorrectFormat(key)) {
      Some(messages("hts.account-number.incorrect-format", appConfig.BankDetailsConfig.accountNumberLength))
    } else if (form.rollNumberTooShort(key) || form.rollNumberTooLong(key)) {
      Some(messages("hts.roll-number.invalid", appConfig.BankDetailsConfig.rollNumberMinLength, appConfig.BankDetailsConfig.rollNumberMaxLength))
    } else if (form.accountNameTooShort(key)) {
      Some(messages("hts.account-name.too-short", appConfig.BankDetailsConfig.accountNameMinLength))
    } else if (form.accountNameTooLong(key)) {
      Some(messages("hts.account-name.too-long", appConfig.BankDetailsConfig.accountNameMaxLength))
    } else {
      None
    }
  }
}
