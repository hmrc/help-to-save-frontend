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

import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.http.Status.OK
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.models.iv._
import uk.gov.hmrc.helptosavefrontend.util.{Logging, toFuture}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[IvConnectorImpl])
trait IvConnector {
  def getJourneyStatus(
    journeyId: JourneyId
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[IvResponse]]
}

@Singleton
class IvConnectorImpl @Inject() (http: HttpClientV2)(implicit val frontendAppConfig: FrontendAppConfig)
    extends IvConnector with Logging {

  override def getJourneyStatus(
    journeyId: JourneyId
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[IvResponse]] = {
    val ivJourneyResultUrl = frontendAppConfig.ivJourneyResultUrl(journeyId)
    val response = http
      .get(url"$ivJourneyResultUrl")
      .execute[Either[UpstreamErrorResponse, HttpResponse]]

    response
      .flatMap {
        case Right(httpResponse) if httpResponse.status == OK =>
          val result = (httpResponse.json \ "result").as[String]
          IvSuccessResponse.fromString(result)
        case Right(httpResponse) =>
          logger.warn(
            s"Unexpected ${httpResponse.status} response getting IV journey status from identity-verification-frontend-service"
          )
          Some(IvUnexpectedResponse(httpResponse))
        case Left(e) =>
          logger.warn(
            s"Unexpected ${e.statusCode} response getting IV journey status from identity-verification-frontend-service"
          )
          Some(IvErrorResponse(e))
      }
      .recoverWith {
        case e: Exception =>
          logger.warn("Error getting IV journey status from identity-verification-frontend-service", e)
          Some(IvErrorResponse(e))
      }
  }
}
