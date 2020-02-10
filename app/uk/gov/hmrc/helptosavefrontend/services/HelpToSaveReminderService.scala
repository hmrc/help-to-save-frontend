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

import java.util.UUID

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject}
import javax.inject.Singleton
import play.api.http.Status
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.helptosavefrontend.connectors.HelpToSaveReminderConnector
import uk.gov.hmrc.helptosavefrontend.models.reminder.HtsUser
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.helptosavefrontend.util.{Email, Logging, Result, maskNino}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[HelpToSaveReminderServiceImpl])
trait HelpToSaveReminderService {

  def updateHtsUser(htsUser: HtsUser)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit]

}

@Singleton
class HelpToSaveReminderServiceImpl @Inject() (helpToSaveReminderConnector: HelpToSaveReminderConnector) extends HelpToSaveReminderService with Logging {

  override def updateHtsUser(htsUser: HtsUser)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit] =
    EitherT(helpToSaveReminderConnector.updateHtsUser(htsUser).map[Either[String, Unit]] { response ⇒

      response.status match {
        case Status.OK ⇒
          Right(())

        case other ⇒
          Left(s"Received unexpected status $other from HTS Reminder Service while trying to update htsUser. Body was ${maskNino(response.body)}")

      }
    }.recover {
      case e ⇒
        Left(s"Encountered error while trying to update htsUser: ${e.getMessage}")
    }
    )

}

