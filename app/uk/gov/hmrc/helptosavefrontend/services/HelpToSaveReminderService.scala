/*
 * Copyright 2020 HM Revenue & Customs
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

import java.time.LocalDate

import com.google.inject.{ImplementedBy, Inject}
import javax.inject.Singleton
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.helptosavefrontend.connectors.HelpToSaveReminderConnector
import uk.gov.hmrc.helptosavefrontend.models.reminder.{CancelHtsUserReminder, HtsUser}
import uk.gov.hmrc.helptosavefrontend.util.{Logging, Result}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[HelpToSaveReminderServiceImpl])
trait HelpToSaveReminderService {

  def updateHtsUser(htsUser: HtsUser)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[HtsUser]
  def getHtsUser(nino: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[HtsUser]
  def cancelHtsUserReminders(cancelHtsUserReminder: CancelHtsUserReminder)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit]

}

@Singleton
class HelpToSaveReminderServiceImpl @Inject() (helpToSaveReminderConnector: HelpToSaveReminderConnector) extends HelpToSaveReminderService with Logging {

  def updateHtsUser(htsUser: HtsUser)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[HtsUser] =
    helpToSaveReminderConnector.updateHtsUser(htsUser)

  def getHtsUser(nino: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[HtsUser] =
    helpToSaveReminderConnector.getHtsUser(nino)

  def cancelHtsUserReminders(cancelHtsUserReminder: CancelHtsUserReminder)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit] =
    helpToSaveReminderConnector.cancelHtsUserReminders(cancelHtsUserReminder)

}

