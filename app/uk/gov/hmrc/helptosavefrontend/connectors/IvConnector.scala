/*
 * Copyright 2017 HM Revenue & Customs
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
import play.mvc.Http.Status.OK
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig.ivUrl
import uk.gov.hmrc.helptosavefrontend.config.WSHttp
import uk.gov.hmrc.helptosavefrontend.models.iv._
import uk.gov.hmrc.helptosavefrontend.util.{Logging, toFuture}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[IvConnectorImpl])
trait IvConnector {
  def getJourneyStatus(journeyId: JourneyId)(implicit hc: HeaderCarrier): Future[Option[IvResponse]]
}

@Singleton
class IvConnectorImpl @Inject() (http: WSHttp) extends IvConnector with Logging {

  override def getJourneyStatus(journeyId: JourneyId)(implicit hc: HeaderCarrier): Future[Option[IvResponse]] = {

    http.GET(s"$ivUrl/${journeyId.Id}").flatMap {

      case r if r.status == OK ⇒
        val result = (r.json \ "result").as[String]
        IvSuccessResponse.fromString(result)

      case r ⇒
        logger.warn(s"Unexpected ${r.status} response getting IV journey status from identity-verification-frontend-service")
        Some(IvUnexpectedResponse(r))

    }.recoverWith {
      case e: Exception ⇒
        logger.warn("Error getting IV journey status from identity-verification-frontend-service", e)
        Some(IvErrorResponse(e))
    }
  }
}
