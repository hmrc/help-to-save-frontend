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

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.http.Status.{NOT_MODIFIED, OK}
import play.api.libs.json.Json
import play.mvc.Http.Status
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.models.reminder.{CancelHtsUserReminder, HtsUserSchedule, UpdateReminderEmail}
import uk.gov.hmrc.helptosavefrontend.util.HttpResponseOps._
import uk.gov.hmrc.helptosavefrontend.util.Result
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}

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
class HelpToSaveReminderConnectorImpl @Inject() (http: HttpClientV2)(implicit frontendAppConfig: FrontendAppConfig)
    extends HelpToSaveReminderConnector {

  private val htsReminderURL = frontendAppConfig.helpToSaveReminderUrl

  private val updateHtsReminderURL = s"$htsReminderURL/help-to-save-reminder/update-htsuser-entity"

  private def htsReminderUserURL = s"$htsReminderURL/help-to-save-reminder/gethtsuser"

  private val cancelHtsReminderURL = s"$htsReminderURL/help-to-save-reminder/delete-htsuser-entity"

  private val emailUpdateHtsReminderURL = s"$htsReminderURL/help-to-save-reminder/update-htsuser-email"

  override def updateHtsUser(
    htsUser: HtsUserSchedule
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[HtsUserSchedule] =
    handle(
      http.post(url"$updateHtsReminderURL").withBody(Json.toJson(htsUser)).execute[HttpResponse],
      _.parseJSON[HtsUserSchedule](),
      "update htsUser"
    )

  override def getHtsUser(nino: String)(implicit hc: HeaderCarrier, ex: ExecutionContext): Result[HtsUserSchedule] =
    handle(
      http.get(url"$htsReminderUserURL/$nino").execute[HttpResponse],
      _.parseJSON[HtsUserSchedule](),
      "get hts user"
    )

  override def updateReminderEmail(
    updateReminderEmail: UpdateReminderEmail
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit] =
    handle(
      http.post(url"$emailUpdateHtsReminderURL").withBody(Json.toJson(updateReminderEmail)).execute[HttpResponse],
      _ => Right(()),
      "update email"
    )

  override def cancelHtsUserReminders(
    cancelHtsUserReminder: CancelHtsUserReminder
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit] =
    for {
      response <- toEitherT(
                   "cancel reminder",
                   http
                     .post(url"$cancelHtsReminderURL")
                     .withBody(Json.toJson(cancelHtsUserReminder))
                     .execute[Either[UpstreamErrorResponse, HttpResponse]]
                 )
      result <- response match {
                 case Right(response) if response.status == OK | response.status == NOT_MODIFIED =>
                   EitherT.rightT[Future, String](())
                 case Right(response) =>
                   EitherT.leftT[Future, Unit](
                     s"Call to 'cancel reminder' came back with status ${response.status}. Body was ${response.body}"
                   )
                 case Left(e) =>
                   EitherT.leftT[Future, Unit](
                     s"Call to 'cancel reminder' came back with status ${e.statusCode}. Body was ${e.getMessage()}"
                   )
               }
    } yield result

  private def toEitherT[T](endpoint: String, future: Future[T])(
    implicit ec: ExecutionContext
  ): EitherT[Future, String, T] =
    EitherT(future.map(Right(_)).recover { case NonFatal(t) => Left(s"Call to $endpoint failed: ${t.getMessage}") })

  private def handle[B](
    resF: Future[HttpResponse],
    ifHTTP200or404: HttpResponse => Either[String, B],
    description: => String
  )(implicit ec: ExecutionContext) =
    for {
      response <- toEitherT(description, resF)
      result <- response.status match {
                 case Status.OK | Status.NOT_FOUND => EitherT.fromEither[Future](ifHTTP200or404(response))
                 case _ =>
                   EitherT.leftT[Future, B](
                     s"Call to $description came back with status ${response.status}. Body was ${response.body}"
                   )
               }
    } yield result

}
