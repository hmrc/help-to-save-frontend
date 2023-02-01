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
import uk.gov.hmrc.govukfrontend.views.Aliases.Details
import uk.gov.hmrc.govukfrontend.views.html.components._

class DetailsHelper {
  private def detailsRow(title: String, htmlContent: String): Details =
    Details(
      classes = "govuk-!-margin-bottom-0",
      summary = Text(title),
      content = HtmlContent(htmlContent)
    )
  def payment(paymentsHowMuch: String, whenWillPaymentsAppear: String, setUpRegularPayment: String)
                    (implicit messages: Messages): List[Details] = {
    val paymentsHowMuchDetails: Details =
      detailsRow(
        messages("hts.help-information.section.payments.s1.title"),
        paymentsHowMuch)
    val setUpRegularPaymentDetails: Details =
      detailsRow(
        messages("hts.help-information.section.payments.s2.title"),
        setUpRegularPayment)
    val whenWillPaymentsAppearDetails: Details =
      detailsRow(
        messages("hts.help-information.section.payments.s3.title"),
        whenWillPaymentsAppear)
    List(paymentsHowMuchDetails, whenWillPaymentsAppearDetails, setUpRegularPaymentDetails)
  }
  def bonuses(whatBonuses: String, highestBalance: String, firstBonus: String,
                     finalBonus: String, bonusExamples: String, bonusPaid: String, howWithdrawalsAffectBonuses: String)
                    (implicit messages: Messages): List[Details] = {
    val whatBonusesDetails: Details =
      detailsRow(
        messages("hts.help-information.section.bonuses.s1.title"),
        whatBonuses)
    val highestBalanceDetails: Details =
      detailsRow(
        messages("hts.help-information.section.bonuses.s2.title"),
        highestBalance)
    val firstBonusDetails: Details =
      detailsRow(
        messages("hts.help-information.section.bonuses.s3.title"),
        firstBonus)
    val finalBonusDetails: Details =
      detailsRow(
        messages("hts.help-information.section.bonuses.s4.title"),
        finalBonus)
    val bonusExamplesDetails: Details =
      detailsRow(
        messages("hts.help-information.section.bonuses.s5.title"),
        bonusExamples)
    val bonusPaidDetails: Details =
      detailsRow(
        messages("hts.help-information.section.bonuses.s6.title"),
        bonusPaid)
    val howWithdrawalsAffectBonusesDetails: Details =
      detailsRow(
        messages("hts.help-information.section.bonuses.s7.title"),
        howWithdrawalsAffectBonuses)
    List(whatBonusesDetails, highestBalanceDetails, firstBonusDetails, finalBonusDetails,
      bonusExamplesDetails, bonusPaidDetails, howWithdrawalsAffectBonusesDetails)
  }
  def withdrawals(withdrawingMoney: String)
                    (implicit messages: Messages): List[Details] = {
    val withdrawingMoneyDetails: Details =
      detailsRow(
        messages("hts.help-information.section.withdrawals.s1.title"),
        withdrawingMoney)
    List(withdrawingMoneyDetails)
  }
  def howAccountWorks(savingsExplained: String, howSignIn: String, whyUseApp: String, howContactYou: String, leaveUk: String,
  stopClaimingBenefits: String, closeAccountEarly: String, deathTerminalIllness: String)
                 (implicit messages: Messages): List[Details] = {
    val savingsExplainedDetails: Details =
      detailsRow(
        messages("hts.help-information.section.how-account-works.s1.title"),
        savingsExplained)
    val howSignInDetails: Details =
      detailsRow(
        messages("hts.help-information.section.how-account-works.s2.title"),
        howSignIn)
    val whyUseAppDetails: Details =
      detailsRow(
        messages("hts.help-information.section.how-account-works.s3.title"),
        whyUseApp)
    val howContactYouDetails: Details =
      detailsRow(
        messages("hts.help-information.section.how-account-works.s4.title"),
        howContactYou)
    val leaveUkDetails: Details =
      detailsRow(
        messages("hts.help-information.section.how-account-works.s4.title"),
        leaveUk)
    val stopClaimingBenefitsDetails: Details =
      detailsRow(
        messages("hts.help-information.section.how-account-works.s6.title"),
        stopClaimingBenefits)
    val closeAccountEarlyDetails: Details =
      detailsRow(
        messages("hts.help-information.section.how-account-works.s8.title"),
        closeAccountEarly)
    val deathTerminalIllnessDetails: Details =
      detailsRow(
        messages("hts.help-information.section.how-account-works.s9.title"),
        deathTerminalIllness)
    List(savingsExplainedDetails, howSignInDetails, whyUseAppDetails, howContactYouDetails, leaveUkDetails,
      stopClaimingBenefitsDetails, closeAccountEarlyDetails, deathTerminalIllnessDetails)
  }
  def whenYourAccountEnds(beforeAccountCloses: String, after4Years: String, savingsAfterAccountCloses: String)
             (implicit messages: Messages): List[Details] = {
    val beforeAccountClosesDetails: Details =
      detailsRow(
        messages("hts.help-information.section.payments.s1.title"),
        beforeAccountCloses)
    val after4YearsDetails: Details =
      detailsRow(
        messages("hts.help-information.section.payments.s2.title"),
        after4Years)
    val savingsAfterAccountClosesDetails: Details =
      detailsRow(
        messages("hts.help-information.section.payments.s3.title"),
        savingsAfterAccountCloses)
    List(beforeAccountClosesDetails, after4YearsDetails, savingsAfterAccountClosesDetails)
  }
  def hmrcApp(appBenefits: String, getApp: String)
             (implicit messages: Messages): List[Details] = {
    val appBenefitsDetails: Details =
      detailsRow(
        messages("hts.help-information.section.hmrc-app.benefits.title"),
        appBenefits)
    val getAppDetails: Details =
      detailsRow(
        messages("hts.help-information.section.hmrc-app.get-the-app.title"),
        getApp)
    List(appBenefitsDetails, getAppDetails)
  }
}
