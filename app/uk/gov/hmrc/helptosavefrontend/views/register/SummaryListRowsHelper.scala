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

import play.api.i18n.Messages
import play.api.mvc.Call
import uk.gov.hmrc.govukfrontend.views.html.components._
import uk.gov.hmrc.helptosavefrontend.controllers.routes
import uk.gov.hmrc.helptosavefrontend.forms.BankDetails
import uk.gov.hmrc.helptosavefrontend.models.HTSSession._
import uk.gov.hmrc.helptosavefrontend.views.helpers.{DateUtils, PeriodUtils}

class SummaryListRowsHelper {
  private def summaryListRow(question: String, answer: String, changeLocation: Call, changeScreenReaderText: String)
                            (implicit messages: Messages): SummaryListRow =
    SummaryListRow(
      key = Key(content = Text(question), classes = "govuk-!-width-one-third"),
      value = Value(content = Text(answer)),
      actions = Some(
        Actions(
          items = Seq(
            ActionItem(
              href = changeLocation.url,
              content = Text(messages("hts.register.create_account.change")),
              visuallyHiddenText = Some(changeScreenReaderText)
            )
          )
        )
      )
    )

  def yourDetailsRow(eligibleWithUserInfo: EligibleWithUserInfo)
                    (implicit messages: Messages): List[SummaryListRow] = {
    val name: SummaryListRow =
      summaryListRow(
        messages("hts.register.create_account.your-details.name"),
        messages(eligibleWithUserInfo.userInfo.forename + " " + eligibleWithUserInfo.userInfo.surname),
        routes.RegisterController.getDetailsAreIncorrect,
        messages("hts.register.create_account.change")
      )
    val dob: SummaryListRow =
      summaryListRow(
        messages("hts.register.create_account.your-details.dob"),
        messages(DateUtils.toLocalisedString(eligibleWithUserInfo.userInfo.dateOfBirth)),
        routes.RegisterController.getDetailsAreIncorrect,
        messages("hts.register.create_account.change")
      )
    val nino: SummaryListRow =
      summaryListRow(
        messages("hts.register.create_account.your-details.nino"),
        messages(display2CharFormat(eligibleWithUserInfo.userInfo.nino)),
        routes.RegisterController.getDetailsAreIncorrect,
        messages("hts.register.create_account.change")
      )
    List(
      name,
      dob,
      nino
    )
  }

  def yourEmailDetailsRow(yourEmail: String, period: String, appConfig: Boolean)
                         (implicit messages: Messages): List[SummaryListRow] = {
    val email: SummaryListRow =
      summaryListRow(
        messages("hts.register.create_account.your-email.email"),
        messages(yourEmail),
        routes.RegisterController.changeEmail,
        messages("hts.register.create_account.change")
      )

    val emailReminder: SummaryListRow = {
      if (appConfig) {
        summaryListRow(
          messages("hts.register.create_account.your-email.email"),
          messages("hts.register.create_account.your-remainder.note"),
          routes.RegisterController.changeEmail,
          messages("hts.register.create_account.change")
        )
      }
      else {
        summaryListRow(
          messages("hts.register.create_account.your-email.email"),
          messages("hts.reminder-confirmation.title.p1-1") + " " +
            PeriodUtils.getMessage(period) + " " + messages("hts.reminder-confirmation.title.p1-2"),
          routes.RegisterController.changeEmail,
          messages("hts.register.create_account.change")
        )
      }
    }
    List(
      email,
      emailReminder
    )
  }

  def yourBankDetailsRow(bankDetails: BankDetails)
                        (implicit messages: Messages): List[SummaryListRow] = {
    val sortCode: SummaryListRow =
      summaryListRow(
        messages("hts.register.create_account.your-bank-details.sort-code"),
        messages(display2CharFormat(bankDetails.sortCode.toString)),
        routes.RegisterController.changeBankDetails,
        messages("hts.register.create_account.change")
      )
    val accountNumber: SummaryListRow =
      summaryListRow(
        messages("hts.register.create_account.your-bank-details.account-number"),
        messages(bankDetails.accountNumber),
        routes.RegisterController.changeBankDetails,
        messages("hts.register.create_account.change")
      )
    val rollNumber: SummaryListRow =
      summaryListRow(
        messages("hts.register.create_account.your-bank-details.roll-number"),
        messages(bankDetails.rollNumber.getOrElse("")),
        routes.RegisterController.changeBankDetails,
        messages("hts.register.create_account.change")
      )
    val accountName: SummaryListRow =
      summaryListRow(
        messages("hts.register.create_account.your-bank-details.account-name"),
        messages(bankDetails.accountName),
        routes.RegisterController.changeBankDetails,
        messages("hts.register.create_account.change")
      )
    List(
      sortCode,
      accountNumber,
      rollNumber,
      accountName
    )
  }

  private def display2CharFormat(str: String): String = {
    str.filterNot((x: Char) => x.isWhitespace || x.toString.matches("-")).grouped(2).mkString(" ")
  }
}
