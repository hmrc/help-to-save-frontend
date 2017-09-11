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

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json.{Reads, Writes}
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig.{keyStoreDomain, keyStoreUrl, sessionCacheKey}
import uk.gov.hmrc.helptosavefrontend.config.WSHttp
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.models.HTSSession
import uk.gov.hmrc.helptosavefrontend.util.Result
import uk.gov.hmrc.http.cache.client.{CacheMap, SessionCache}
import uk.gov.hmrc.play.config.{AppName, ServicesConfig}
import uk.gov.hmrc.play.http._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@ImplementedBy(classOf[SessionCacheConnectorImpl])
trait SessionCacheConnector {

  val sessionKey: String

  def put(body: HTSSession)(implicit writes: Writes[HTSSession], hc: HeaderCarrier, ec: ExecutionContext): Result[CacheMap]

  def get(implicit reads: Reads[HTSSession], hc: HeaderCarrier, ec: ExecutionContext): Result[Option[HTSSession]]

}

@Singleton
class SessionCacheConnectorImpl @Inject() (val http: WSHttp, metrics: Metrics)
  extends SessionCacheConnector with SessionCache with ServicesConfig with AppName {

  override def defaultSource: String = appName

  val sessionKey: String = sessionCacheKey

  override def baseUri: String = keyStoreUrl

  override def domain: String = keyStoreDomain

  def put(body: HTSSession)(implicit writes: Writes[HTSSession], hc: HeaderCarrier, ec: ExecutionContext): Result[CacheMap] =
    EitherT[Future, String, CacheMap]{
      val timerContext = metrics.keystoreWriteTimer.time()

      cache[HTSSession](sessionKey, body)(writes, hc).map{ cacheMap ⇒
        val _ = timerContext.stop()
        Right(cacheMap)
      }.recover {
        case NonFatal(e) ⇒
          val _ = timerContext.stop()
          Left(e.getMessage)
      }
    }

  def get(implicit reads: Reads[HTSSession], hc: HeaderCarrier, ec: ExecutionContext): Result[Option[HTSSession]] =
    EitherT[Future, String, Option[HTSSession]]{
      val timerContext = metrics.keystoreReadTimer.time()

      fetchAndGetEntry[HTSSession](sessionKey)(hc, reads).map{ session ⇒
        val _ = timerContext.stop()
        Right(session)
      }.recover {
        case NonFatal(e) ⇒
          val _ = timerContext.stop()
          Left(e.getMessage)
      }
    }

}
