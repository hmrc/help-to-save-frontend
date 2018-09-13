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

package uk.gov.hmrc.helptosavefrontend.services

import java.util.UUID

import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.http.Status
import uk.gov.hmrc.helptosavefrontend.connectors.BarsConnector
import uk.gov.hmrc.helptosavefrontend.forms.BankDetails
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.util.{Logging, NINOLogMessageTransformer}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[BarsServiceImpl])
trait BarsService {

  type BarsResponseType = Future[Either[String, Boolean]]

  def validate(bankDetails: BankDetails)(implicit hc: HeaderCarrier, ec: ExecutionContext): BarsResponseType

}

@Singleton
class BarsServiceImpl @Inject() (barsConnector: BarsConnector,
                                 metrics:       Metrics)(implicit transformer: NINOLogMessageTransformer) extends BarsService with Logging {

  override def validate(bankDetails: BankDetails)(implicit hc: HeaderCarrier, ec: ExecutionContext): BarsResponseType = {
    val timerContext = metrics.barsTimer.time()
    val trackingId = UUID.randomUUID()
    barsConnector.validate(bankDetails, trackingId).map[Either[String, Boolean]] {
      response ⇒
        val _ = timerContext.stop()
        response.status match {
          case Status.OK ⇒
            (response.json \ "accountNumberWithSortCodeIsValid").asOpt[Boolean] match {
              case Some(result) ⇒ Right(result)
              case None ⇒
                logger.warn(s"error parsing the response from bars check, trackingId = $trackingId,  body = ${response.body}")
                Left(s"error parsing the response json from bars check")
            }
          case other: Int ⇒
            //Do we need pager duty alert here ?
            metrics.barsErrorCounter.inc()
            logger.warn(s"unexpected status from bars check, trackingId = $trackingId, status=$other, body = ${response.body}")
            Left("unexpected status from bars check")
        }
    }.recover {
      case e ⇒
        metrics.barsErrorCounter.inc()
        logger.warn(s"unexpected error from bars check, trackingId = $trackingId, error=${e.getMessage}")
        Left("unexpected error from bars check")
    }
  }
}
