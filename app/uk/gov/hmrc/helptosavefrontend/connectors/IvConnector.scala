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

import com.google.inject.{ImplementedBy, Singleton}
import play.api.Logger
import play.mvc.Http.Status.OK
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.{FrontendAppConfig, WSHttp}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[IvConnectorImpl])
trait IvConnector {
  def getJourneyStatus(journeyId: JourneyId)(implicit hc: HeaderCarrier): Future[IvResponse]
}

@Singleton
class IvConnectorImpl extends IvConnector {

  val identityVerificationURL: String = FrontendAppConfig.IdentityVerificationURL

  val http = WSHttp

  override def getJourneyStatus(journeyId: JourneyId)(implicit hc: HeaderCarrier): Future[IvResponse] = {

    http.GET(s"$identityVerificationURL/${journeyId.Id}").flatMap {

      case r if r.status == OK ⇒
        val result = (r.json \ "result").as[String]
        Future.successful(IvSuccessResponse(result))

      case r =>
        Logger.warn(s"Unexpected ${r.status} response getting IV journey status from identity-verification-frontend-service")
        Future.successful(IvUnexpectedResponse(r))

    }.recoverWith { case e: Exception =>
      Logger.warn("Error getting IV journey status from identity-verification-frontend-service", e)
      Future.successful(IvErrorResponse(e))
    }
  }
}
