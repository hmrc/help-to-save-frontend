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

import java.util.UUID

import cats.data.EitherT
import cats.syntax.either._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json._
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.connectors.HelpToSaveConnectorImpl._
import uk.gov.hmrc.helptosavefrontend.http.HttpClient.HttpClientOps
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.account.Account
import uk.gov.hmrc.helptosavefrontend.models.account.AccountNumber
import uk.gov.hmrc.helptosavefrontend.models.eligibility.{EligibilityCheckResponse, EligibilityCheckResultType}
import uk.gov.hmrc.helptosavefrontend.models.register.CreateAccountRequest
import uk.gov.hmrc.helptosavefrontend.models.reminder.HtsUser
import uk.gov.hmrc.helptosavefrontend.models.userinfo.{MissingUserInfo, NSIPayload}
import uk.gov.hmrc.helptosavefrontend.util.HttpResponseOps._
import uk.gov.hmrc.helptosavefrontend.util.{Email, Result, base64Encode, maskNino}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@ImplementedBy(classOf[HelpToSaveReminderConnectorImpl])
trait HelpToSaveReminderConnector {

  def updateHtsUser(htsUser: HtsUser)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[HttpResponse]

}

@Singleton
class HelpToSaveReminderConnectorImpl @Inject() (http: HttpClient)(implicit frontendAppConfig: FrontendAppConfig) extends HelpToSaveReminderConnector {

  private val updateHtsReminderURL: String = frontendAppConfig.updateHtsReminderUrl

  private val emptyQueryParameters: Map[String, String] = Map.empty[String, String]

  override def updateHtsUser(htsUser: HtsUser)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    http.post(updateHtsReminderURL, htsUser)

}

object HelpToSaveReminderConnectorImpl {

  private[connectors] case class GetEmailResponse(email: Option[String])

  private[connectors] object GetEmailResponse {
    implicit val format: Format[GetEmailResponse] = Json.format[GetEmailResponse]
  }

}
