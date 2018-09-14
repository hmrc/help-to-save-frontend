/*
 * Copyright 2018 HM Revenue & Customs
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

import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json.Json
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.forms.BankDetails
import uk.gov.hmrc.helptosavefrontend.http.HttpClient.HttpClientOps
import uk.gov.hmrc.helptosavefrontend.util.Logging
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[BarsConnectorImpl])
trait BarsConnector {

  def validate(bankDetails: BankDetails, trackingId: UUID)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse]
}

@Singleton
class BarsConnectorImpl @Inject() (http: HttpClient)(implicit appConfig: FrontendAppConfig) extends BarsConnector with Logging {

  private val barsEndpoint: String = s"${appConfig.barsUrl}/validateBankDetails"

  private val headers = Map("User-Agent" -> "help-to-save-frontend", "Content-Type" -> "application/json")

  override def validate(bankDetails: BankDetails, trackingId: UUID)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    http.post(barsEndpoint, bodyJson(bankDetails), headers.+("X-Tracking-Id" -> trackingId.toString))

  private def bodyJson(details: BankDetails) = {

    Json.parse(
      s"""{"account":{"sortCode":"${details.sortCode}","accountNumber":"${details.accountNumber}"}}"""
    )
  }
}
