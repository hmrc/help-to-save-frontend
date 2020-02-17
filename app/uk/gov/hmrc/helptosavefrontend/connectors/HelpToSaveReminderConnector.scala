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

package uk.gov.hmrc.helptosavefrontend.connectors

import java.time.LocalDate
import java.util.UUID

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject, Singleton}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.http.HttpClient.HttpClientOps
import uk.gov.hmrc.helptosavefrontend.models.account.AccountNumber
import uk.gov.hmrc.helptosavefrontend.models.reminder.HtsUser
import uk.gov.hmrc.helptosavefrontend.util.HttpResponseOps._
import uk.gov.hmrc.helptosavefrontend.util.Result
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@ImplementedBy(classOf[HelpToSaveReminderConnectorImpl])
trait HelpToSaveReminderConnector {

  def updateHtsUser(htsUser: HtsUser)(implicit hc: HeaderCarrier, ex: ExecutionContext): Result[HtsUser]
  def getHtsUser(nino: String)(implicit hc: HeaderCarrier, ex: ExecutionContext): Result[HtsUser]

}

@Singleton
class HelpToSaveReminderConnectorImpl @Inject() (http: HttpClient)(implicit frontendAppConfig: FrontendAppConfig) extends HelpToSaveReminderConnector {

  private val htsReminderURL = frontendAppConfig.helpToSaveReminderUrl

  private val updateHtsReminderURL = s"$htsReminderURL/help-to-save-reminder/update-htsuser-entity"

  private def getHtsReminderUserURL(nino: String) = s"$htsReminderURL/help-to-save-reminder/getIfHtsUserExists/${nino}"
  /* val nino: Nino = Nino("AE123456D")

  val htsUser = HtsUser(nino, "user@gmail.com", "new user", true, Seq(1), LocalDate.parse("2000-01-01"), 1)
*/

  private val emptyQueryParameters: Map[String, String] = Map.empty[String, String]

  override def updateHtsUser(htsUser: HtsUser)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[HtsUser] =
    handlePost(updateHtsReminderURL, htsUser, _.parseJSON[HtsUser](), "update htsUser", identity)

  override def getHtsUser(nino: String)(implicit hc: HeaderCarrier, ex: ExecutionContext): Result[HtsUser] =
    handleGet(getHtsReminderUserURL(nino), emptyQueryParameters, _.parseJSON[HtsUser](), "get hts user", identity)

  private def handlePost[A, B](url:         String,
                               body:        HtsUser,
                               ifHTTP200:   HttpResponse ⇒ Either[B, A],
                               description: ⇒ String,
                               toError:     String ⇒ B)(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, B, A] =
    handle(http.post(url, body), ifHTTP200, description, toError)

  private def handleGet[A, B](url:             String,
                              queryParameters: Map[String, String],
                              ifHTTP200:       HttpResponse ⇒ Either[B, A],
                              description:     ⇒ String,
                              toError:         String ⇒ B)(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, B, A] =
    handle(http.get(url, queryParameters), ifHTTP200, description, toError)

  private def handle[A, B](resF:        Future[HttpResponse],
                           ifHTTP200:   HttpResponse ⇒ Either[B, A],
                           description: ⇒ String,
                           toError:     String ⇒ B)(implicit ec: ExecutionContext) = {
    EitherT(resF.map { response ⇒
      if (response.status == 200) {
        ifHTTP200(response)
      } else {
        Left(toError(s"Call to $description came back with status ${response.status}. Body was ${(response.body)}"))
      }
    }.recover {
      case NonFatal(t) ⇒ Left(toError(s"Call to $description failed: ${t.getMessage}"))
    })
  }

}
