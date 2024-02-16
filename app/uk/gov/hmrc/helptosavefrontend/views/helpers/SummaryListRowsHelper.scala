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

import play.api.i18n.Messages
import play.api.mvc.Call
import uk.gov.hmrc.govukfrontend.views.html.components._
import uk.gov.hmrc.helptosavefrontend.controllers.routes
import uk.gov.hmrc.helptosavefrontend.forms.BankDetails
import uk.gov.hmrc.helptosavefrontend.models.HTSSession._

class SummaryListRowsHelper {
  def summaryListRow(
    question: String,
    answer: String,
    changeLocation: Call,
    changeScreenReaderText: String,
    changeLabel: Option[String]
  )(implicit messages: Messages): SummaryListRow =
    SummaryListRow(
      key = Key(content = Text(question), classes = "govuk-!-width-one-third"),
      value = Value(content = Text(answer)),
      classes = if (changeLabel.isEmpty) "govuk-summary-list__row--no-actions" else "",
      actions = changeLabel.map(
        label =>
          Actions(
            items = Seq(
              ActionItem(
                href = changeLocation.url,
                content = Text(label),
                visuallyHiddenText = Some(changeScreenReaderText)
              )
            )
          )
        )
      else None
    SummaryListRow(
      key = Key(content = Text(question), classes = "govuk-!-width-one-third"),
      value = Value(content = Text(answer)),
      classes = if (changeLabel.isDefined) "govuk-summary-list__row--no-actions" else "",
      actions = changeLabel.map(
        label =>
          Actions(
            items = Seq(
              ActionItem(
                href = changeLocation.url,
                content = Text(label),
                visuallyHiddenText = Some(changeScreenReaderText)
              )
            )
          )
      )
    )
  def yourDetailsRow(eligibleWithUserInfo: EligibleWithUserInfo)(implicit messages: Messages): List[SummaryListRow] = {
    val name: SummaryListRow =
      summaryListRow(
        messages("hts.register.create_account.your-details.name"),
        messages(eligibleWithUserInfo.userInfo.forename + " " + eligibleWithUserInfo.userInfo.surname),
        routes.RegisterController.getDetailsAreIncorrect,
        messages("hts.register.create_account.your-details.name"),
        changeLabel = Some(messages("hts.register.create_account.change"))
      )
    val dob: SummaryListRow =
      summaryListRow(
        messages("hts.register.create_account.your-details.dob"),
        messages(DateUtils.toLocalisedString(eligibleWithUserInfo.userInfo.dateOfBirth)),
        routes.RegisterController.getDetailsAreIncorrect,
        messages("hts.register.create_account.your-details.dob"),
        changeLabel = None
      )
    val nino: SummaryListRow =
      summaryListRow(
        messages("hts.register.create_account.your-details.nino"),
        messages(display2CharFormat(eligibleWithUserInfo.userInfo.nino)),
        routes.RegisterController.getDetailsAreIncorrect,
        messages("hts.register.create_account.your-details.nino"),
        changeLabel = None
      )
    List(
      name,
      dob,
      nino
    )
  }

  def yourEmailDetailsRow(yourEmail: String, period: String, areRemindersEnabled: Boolean)(
    implicit messages: Messages
  ): List[SummaryListRow] = {
    val email: SummaryListRow =
      summaryListRow(
        messages("hts.register.create_account.your-email.email"),
        messages(yourEmail),
        routes.RegisterController.changeEmail,
        messages("hts.register.create_account.your-email.email"),
        changeLabel = Some(messages("hts.register.create_account.change"))
      )

    val emailReminder: SummaryListRow = {
      if (period.matches("none")) {
        summaryListRow(
          messages("hts.email-saving-remainders.title.h1"),
          messages("hts.register.create_account.your-remainder.note"),
          routes.RegisterController.changeReminder,
          messages("hts.email-saving-remainders.title.h1"),
          changeLabel = Some(messages("hts.register.create_account.change"))
        )
      } else {
        summaryListRow(
          messages("hts.email-saving-remainders.title.h1"),
          messages("hts.reminder-confirmation.title.p1-1") + " " +
            PeriodUtils.getMessage(period) + " " + messages("hts.reminder-confirmation.title.p1-2"),
          routes.RegisterController.changeReminder,
          messages("hts.email-saving-remainders.title.h1"),
          changeLabel = Some(messages("hts.register.create_account.change"))
        )
      }
    }
    if (areRemindersEnabled) {
      List(
        email,
        emailReminder
      )
    } else {
      List(email)
    }
  }

  def yourBankDetailsRow(bankDetails: BankDetails)(implicit messages: Messages): List[SummaryListRow] = {
    val sortCode: SummaryListRow =
      summaryListRow(
        messages("hts.register.create_account.your-bank-details.sort-code"),
        messages(display2CharFormat(bankDetails.sortCode.toString)),
        routes.RegisterController.changeBankDetails,
        messages("hts.register.create_account.your-bank-details.sort-code"),
        changeLabel = Some(messages("hts.register.create_account.change"))
      )
    val accountNumber: SummaryListRow =
      summaryListRow(
        messages("hts.register.create_account.your-bank-details.account-number"),
        messages(bankDetails.accountNumber),
        routes.RegisterController.changeBankDetails,
        messages("hts.register.create_account.your-bank-details.account-number"),
        changeLabel = Some(messages("hts.register.create_account.change"))
      )
    val rollNumber: Option[SummaryListRow] = {
      if (bankDetails.rollNumber.nonEmpty) {
        Some(
          summaryListRow(
            messages("hts.register.create_account.your-bank-details.roll-number"),
            messages(bankDetails.rollNumber.getOrElse("")),
            routes.RegisterController.changeBankDetails,
            messages("hts.register.create_account.your-bank-details.roll-number"),
            changeLabel = Some(messages("hts.register.create_account.change"))
          )
        )
      } else {
        None
      }

    }
    val accountName: SummaryListRow =
      summaryListRow(
        messages("hts.register.create_account.your-bank-details.account-name"),
        messages(bankDetails.accountName),
        routes.RegisterController.changeBankDetails,
        messages("hts.register.create_account.your-bank-details.account-name"),
        changeLabel = Some(messages("hts.register.create_account.change"))
      )
    List(
      Some(accountName),
      Some(sortCode),
      Some(accountNumber),
      rollNumber
    ).collect { case Some(r) => r }
  }

  private def display2CharFormat(str: String): String =
    str.filterNot((x: Char) => x.isWhitespace || x.toString.matches("-")).grouped(2).mkString(" ")
}
