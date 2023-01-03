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
    handlePost(updateHtsReminderURL, htsUser, _.parseJSON[HtsUserSchedule](), "update htsUser", identity)

  override def getHtsUser(nino: String)(implicit hc: HeaderCarrier, ex: ExecutionContext): Result[HtsUserSchedule] =
    handleGet(getHtsReminderUserURL(nino), emptyQueryParameters, _.parseJSON[HtsUserSchedule](), "get hts user", identity)

  override def cancelHtsUserReminders(
    cancelHtsUserReminder: CancelHtsUserReminder
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit] =
    handlePostCancel(cancelHtsReminderURL, cancelHtsUserReminder, _ ⇒ Right(()), "cancel reminder", identity)

  private def handlePost[A, B](
    url: String,
    body: HtsUserSchedule,
    ifHTTP200: HttpResponse ⇒ Either[B, A],
    description: ⇒ String,
    toError: String ⇒ B
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, B, A] =
    handle(http.post(url, body), ifHTTP200, description, toError)

  private def handlePostCancel[A, B](
    url: String,
    body: CancelHtsUserReminder,
    ifHTTP200: HttpResponse ⇒ Either[B, A],
    description: ⇒ String,
    toError: String ⇒ B
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, B, A] =
    handle(http.post(url, body), ifHTTP200, description, toError)

  private def handleGet[A, B](
    url: String,
    queryParameters: Map[String, String],
    ifHTTP200: HttpResponse ⇒ Either[B, A],
    description: ⇒ String,
    toError: String ⇒ B
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, B, A] =
    handle(http.get(url, queryParameters), ifHTTP200, description, toError)

  private def handle[A, B](
    resF: Future[HttpResponse],
    ifHTTP200: HttpResponse ⇒ Either[B, A],
    description: ⇒ String,
    toError: String ⇒ B
  )(implicit ec: ExecutionContext) =
    EitherT(
      resF
        .map { response ⇒
          if (response.status == Status.OK || response.status == Status.NOT_FOUND) {
            ifHTTP200(response)
          } else {
            Left(toError(s"Call to $description came back with status ${response.status}. Body was ${(response.body)}"))
          }
        }
        .recover {
          case NonFatal(t) ⇒ Left(toError(s"Call to $description failed: ${t.getMessage}"))
        }
    )

  override def updateReminderEmail(
    updateReminderEmail: UpdateReminderEmail
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit] =
    handlePostEmailUpdate(emailUpdateHtsReminderURL, updateReminderEmail, _ ⇒ Right(()), "update email", identity)

  private def handlePostEmailUpdate[A, B](
    url: String,
    body: UpdateReminderEmail,
    ifHTTP200: HttpResponse ⇒ Either[B, A],
    description: ⇒ String,
    toError: String ⇒ B
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, B, A] =
    handle(http.post(url, body), ifHTTP200, description, toError)

}
