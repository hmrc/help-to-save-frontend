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
import cats.instances.either._
import cats.syntax.either._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json.{Reads, Writes}
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.http.HttpClient.HttpClientOps
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.models.HTSSession
import uk.gov.hmrc.helptosavefrontend.util.Result
import uk.gov.hmrc.helptosavefrontend.util.HttpResponseOps._
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException}
import uk.gov.hmrc.http.cache.client.{CacheMap, SessionCache}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@ImplementedBy(classOf[SessionCacheConnectorImpl])
trait SessionCacheConnector {

  val sessionKey: String

  def put(body: HTSSession)(implicit writes: Writes[HTSSession], hc: HeaderCarrier, ec: ExecutionContext): Result[CacheMap]

  def get(implicit reads: Reads[HTSSession], hc: HeaderCarrier, ec: ExecutionContext): Result[Option[HTSSession]]

}

@Singleton
class SessionCacheConnectorImpl @Inject() (val http: HttpClient,
                                           metrics:  Metrics)(implicit val frontendAppConfig: FrontendAppConfig)
  extends SessionCacheConnector with SessionCache {

  override def defaultSource: String = frontendAppConfig.appName

  val sessionKey: String = frontendAppConfig.sessionCacheKey

  override def baseUri: String = frontendAppConfig.keyStoreUrl

  override def domain: String = frontendAppConfig.keyStoreDomain

  def put(newSession: HTSSession)(implicit writes: Writes[HTSSession], hc: HeaderCarrier, ec: ExecutionContext): Result[CacheMap] = {

      def doUpdate(newSession: HTSSession,
                   oldSession: Option[HTSSession])(implicit writes: Writes[HTSSession],
                                                   hc: HeaderCarrier, ec: ExecutionContext): Future[Either[String, CacheMap]] = {
        val timerContext = metrics.keystoreWriteTimer.time()
        val sessionToStore = oldSession.fold(
          newSession
        )(existing ⇒
          HTSSession(
            eligibilityCheckResult = newSession.eligibilityCheckResult.orElse(existing.eligibilityCheckResult),
            confirmedEmail         = newSession.confirmedEmail.orElse(existing.confirmedEmail),
            pendingEmail           = newSession.pendingEmail.orElse(existing.pendingEmail),
            ivURL                  = newSession.ivURL.orElse(existing.ivURL),
            ivSuccessURL           = newSession.ivSuccessURL.orElse(existing.ivSuccessURL),
            bankDetails            = newSession.bankDetails,
            changingDetails        = newSession.changingDetails,
            accountNumber          = newSession.accountNumber,
            hasSelectedEmail       = newSession.hasSelectedEmail
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

  override def get(uri: String)(implicit hc: HeaderCarrier, executionContext: ExecutionContext): Future[CacheMap] =
    http.get(uri).flatMap { r ⇒
      r.status match {
        case 200 ⇒
          r.parseJSON[CacheMap]() match {
            case Right(c: CacheMap) ⇒ Future.successful(c)
            case Left(e)            ⇒ Future.failed(new Exception(s"Could not parse response: $e"))
          }
        case 404 ⇒ Future.failed(new NotFoundException(""))
      }
    }

  override def put[T](uri: String, body: T)(implicit hc: HeaderCarrier, wts: Writes[T], executionContext: ExecutionContext): Future[CacheMap] =
    http.put(uri, body).flatMap{
      _.parseJSON[CacheMap]() match {
        case Right(c: CacheMap) ⇒ Future.successful(c)
        case Left(e)            ⇒ Future.failed(new Exception(s"Could not parse response: $e"))
      }
    }

}
