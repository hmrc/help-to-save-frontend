/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.helptosavefrontend.services

import com.google.inject.{ImplementedBy, Inject}
import javax.inject.Singleton
import uk.gov.hmrc.helptosavefrontend.connectors.HelpToSaveReminderConnector
import uk.gov.hmrc.helptosavefrontend.models.reminder.{CancelHtsUserReminder, HtsUserSchedule, UpdateReminderEmail}
import uk.gov.hmrc.helptosavefrontend.util.{Logging, Result}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[HelpToSaveReminderServiceImpl])
trait HelpToSaveReminderService {

  def updateHtsUser(htsUser: HtsUserSchedule)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[HtsUserSchedule]
  def getHtsUser(nino: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[HtsUserSchedule]
  def cancelHtsUserReminders(
    cancelHtsUserReminder: CancelHtsUserReminder
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit]
  def updateReminderEmail(
    updateReminderEmail: UpdateReminderEmail
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit]
}

@Singleton
class HelpToSaveReminderServiceImpl @Inject() (helpToSaveReminderConnector: HelpToSaveReminderConnector)
    extends HelpToSaveReminderService with Logging {

  def updateHtsUser(htsUserSchedule: HtsUserSchedule)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[HtsUserSchedule] =
    helpToSaveReminderConnector.updateHtsUser(htsUserSchedule)

  def getHtsUser(nino: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[HtsUserSchedule] =
    helpToSaveReminderConnector.getHtsUser(nino)

  def cancelHtsUserReminders(
    cancelHtsUserReminder: CancelHtsUserReminder
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit] =
    helpToSaveReminderConnector.cancelHtsUserReminders(cancelHtsUserReminder)

  def updateReminderEmail(
    updateReminderEmail: UpdateReminderEmail
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit] =
    helpToSaveReminderConnector.updateReminderEmail(updateReminderEmail)

}
