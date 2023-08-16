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

package uk.gov.hmrc.helptosavefrontend.connectors

import play.mvc.Http.Status
import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject, Singleton}
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.http.HttpClient.HttpClientOps
import uk.gov.hmrc.helptosavefrontend.models.reminder.{CancelHtsUserReminder, HtsUserSchedule, UpdateReminderEmail}
import uk.gov.hmrc.helptosavefrontend.util.HttpResponseOps._
import uk.gov.hmrc.helptosavefrontend.util.Result
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@ImplementedBy(classOf[HelpToSaveReminderConnectorImpl])
trait HelpToSaveReminderConnector {

  def updateHtsUser(htsUser: HtsUserSchedule)(implicit hc: HeaderCarrier, ex: ExecutionContext): Result[HtsUserSchedule]
  def getHtsUser(nino: String)(implicit hc: HeaderCarrier, ex: ExecutionContext): Result[HtsUserSchedule]
  def cancelHtsUserReminders(
    cancelHtsUserReminder: CancelHtsUserReminder
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit]
  def updateReminderEmail(
    updateReminderEmail: UpdateReminderEmail
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit]

}

@Singleton
class HelpToSaveReminderConnectorImpl @Inject() (http: HttpClient)(implicit frontendAppConfig: FrontendAppConfig)
    extends HelpToSaveReminderConnector {

  private val htsReminderURL = frontendAppConfig.helpToSaveReminderUrl

  private val updateHtsReminderURL = s"$htsReminderURL/help-to-save-reminder/update-htsuser-entity"

  private def getHtsReminderUserURL(nino: String) = s"$htsReminderURL/help-to-save-reminder/gethtsuser/$nino"

  private val cancelHtsReminderURL = s"$htsReminderURL/help-to-save-reminder/delete-htsuser-entity"

  private val emailUpdateHtsReminderURL = s"$htsReminderURL/help-to-save-reminder/update-htsuser-email"

  private val emptyQueryParameters: Map[String, String] = Map.empty[String, String]

  override def updateHtsUser(htsUser: HtsUserSchedule)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[HtsUserSchedule] =
    handle(http.post(updateHtsReminderURL, htsUser), _.parseJSON[HtsUserSchedule](), "update htsUser")

  override def getHtsUser(nino: String)(implicit hc: HeaderCarrier, ex: ExecutionContext): Result[HtsUserSchedule] =
    handle(http.get(getHtsReminderUserURL(nino)), _.parseJSON[HtsUserSchedule](), "get hts user")

  private def toEitherT[T](endpoint : String, future : Future[T])(implicit ec : ExecutionContext): EitherT[Future, String, T] =
    EitherT(future.map(Right(_)).recover { case NonFatal(t) => Left(s"Call to $endpoint failed: ${t.getMessage}") })

  override def cancelHtsUserReminders(
    cancelHtsUserReminder: CancelHtsUserReminder
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit] =
    for {
      response <- toEitherT("cancel reminder", http.post(cancelHtsReminderURL, cancelHtsUserReminder))
      result <- response.status match {
        case Status.OK | Status.NOT_MODIFIED => EitherT.rightT[Future, String]()
        case _ => EitherT.leftT[Future, Unit](s"Call to 'cancel reminder' came back with status ${response.status}. Body was ${response.body}")
      }
    } yield result

  private def handle[B](
    resF: Future[HttpResponse],
    ifHTTP200or404: HttpResponse => Either[String, B],
    description: => String
  )(implicit ec: ExecutionContext) =
    for {
      response <- toEitherT(description, resF)
      result <- response.status match {
        case Status.OK | Status.NOT_FOUND => EitherT.fromEither[Future](ifHTTP200or404(response))
        case _ => EitherT.leftT[Future, B](s"Call to $description came back with status ${response.status}. Body was ${response.body}")
      }
    } yield result

  override def updateReminderEmail(
    updateReminderEmail: UpdateReminderEmail
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit] =
    handle(http.post(emailUpdateHtsReminderURL, updateReminderEmail), _ => Right(()), "update email")

}
