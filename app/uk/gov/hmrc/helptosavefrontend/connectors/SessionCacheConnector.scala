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

import cats.data.EitherT
import cats.instances.future._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json.{Reads, Writes}
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig.{keyStoreDomain, keyStoreUrl, sessionCacheKey}
import uk.gov.hmrc.helptosavefrontend.config.WSHttp
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.models.HTSSession
import uk.gov.hmrc.helptosavefrontend.util.Result
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.{CacheMap, SessionCache}
import uk.gov.hmrc.play.config.AppName

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
  extends SessionCacheConnector with SessionCache with AppName {

  override def defaultSource: String = appName

  val sessionKey: String = sessionCacheKey

  override def baseUri: String = keyStoreUrl

  override def domain: String = keyStoreDomain

  def put(newSession: HTSSession)(implicit writes: Writes[HTSSession], hc: HeaderCarrier, ec: ExecutionContext): Result[CacheMap] = {

      def doUpdate(newSession: HTSSession,
                   oldSession: Option[HTSSession])(implicit writes: Writes[HTSSession],
                                                   hc: HeaderCarrier, ec: ExecutionContext): Future[Either[String, CacheMap]] = {
        val timerContext = metrics.keystoreWriteTimer.time()
        val sessionToStore = oldSession.fold(
          newSession
        )(existing ⇒
          existing.copy(eligibilityCheckResult = newSession.eligibilityCheckResult,
                        confirmedEmail         = newSession.confirmedEmail,
                        pendingEmail           = newSession.pendingEmail
          )
        )

        cache[HTSSession](sessionKey, sessionToStore).map { cacheMap ⇒
          val _ = timerContext.stop()
          Right(cacheMap)
        }.recover {
          case NonFatal(e) ⇒
            val _ = timerContext.stop()
            metrics.keystoreWriteErrorCounter.inc()
            Left(s"error during storing session in key-store: ${e.getMessage}")
        }
      }

    for {
      oldSession ← get
      result ← EitherT(doUpdate(newSession, oldSession))
    } yield result
  }

  def get(implicit reads: Reads[HTSSession], hc: HeaderCarrier, ec: ExecutionContext): Result[Option[HTSSession]] =
    EitherT[Future, String, Option[HTSSession]] {
      val timerContext = metrics.keystoreReadTimer.time()

      fetchAndGetEntry[HTSSession](sessionKey)(hc, reads, ec).map { session ⇒
        val _ = timerContext.stop()
        Right(session)
      }.recover {
        case NonFatal(e) ⇒
          val _ = timerContext.stop()
          metrics.keystoreReadErrorCounter.inc()
          Left(e.getMessage)
      }
    }

}
