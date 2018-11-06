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

package uk.gov.hmrc.helptosavefrontend.repo

import cats.data.{EitherT, OptionT}
import cats.instances.either._
import cats.instances.future._
import cats.syntax.either._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json.{Json, Reads, Writes}
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.cache.model.Id
import uk.gov.hmrc.cache.repository.CacheMongoRepository
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.models.HTSSession
import uk.gov.hmrc.helptosavefrontend.util.{Result, toFuture}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[SessionStoreImpl])
trait SessionStore {

  def get(implicit reads: Reads[HTSSession], hc: HeaderCarrier): Result[Option[HTSSession]]

  def store(body: HTSSession)(implicit writes: Writes[HTSSession], hc: HeaderCarrier): Result[Unit]
}

@Singleton
class SessionStoreImpl @Inject() (mongo: ReactiveMongoComponent, metrics: Metrics)(implicit appConfig: FrontendAppConfig, ec: ExecutionContext) extends SessionStore {

  private val expireAfterSeconds = appConfig.mongoSessionExpireAfter.toSeconds

  private val cacheRepository = new CacheMongoRepository("sessions", expireAfterSeconds)(mongo.mongoConnector.db, ec)

  private type EitherStringOr[A] = Either[String, A]

  override def get(implicit reads: Reads[HTSSession], hc: HeaderCarrier): Result[Option[HTSSession]] = {

    EitherT(hc.sessionId.map(_.value) match {
      case Some(sessionId) ⇒
        val timerContext = metrics.sessionReadTimer.time()
        cacheRepository.findById(Id(sessionId)).map { maybeCache ⇒
          val response: OptionT[EitherStringOr, HTSSession] = for {
            cache ← OptionT.fromOption[EitherStringOr](maybeCache)
            data ← OptionT.fromOption[EitherStringOr](cache.data)
            result ← OptionT.liftF[EitherStringOr, HTSSession](
              (data \ "htsSession").validate[HTSSession].asEither.leftMap(e ⇒ s"Could not parse session data from mongo: ${e.mkString("; ")}"))
          } yield result

          val _ = timerContext.stop()

          response.value

        }.recover {
          case e ⇒
            val _ = timerContext.stop()
            metrics.sessionReadErrorCounter.inc()
            Left(e.getMessage)
        }

      case None ⇒
        Left("can't query mongo dueto no sessionId in the HeaderCarrier")
    })
  }

  override def store(newSession: HTSSession)(implicit writes: Writes[HTSSession], hc: HeaderCarrier): Result[Unit] = {

      def doUpdate(newSession: HTSSession, oldSession: Option[HTSSession]): Future[Either[String, Unit]] = {
        hc.sessionId.map(_.value) match {
          case Some(sessionId) ⇒
            val timerContext = metrics.sessionStoreWriteTimer.time()
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

            cacheRepository.createOrUpdate(Id(sessionId), "htsSession", Json.toJson(sessionToStore))
              .map[Either[String, Unit]] { dbUpdate ⇒
                if (dbUpdate.writeResult.inError) {
                  Left(dbUpdate.writeResult.errMsg.getOrElse("unknown error during inserting session data in mongo"))
                } else {
                  val _ = timerContext.stop()
                  Right(())
                }
              }.recover {
                case e ⇒
                  val _ = timerContext.stop()
                  metrics.sessionStoreWriteErrorCounter.inc()
                  Left(e.getMessage)
              }

          case None ⇒
            Left("can't store HTSSession in mongo dueto no sessionId in the HeaderCarrier")
        }
      }

    for {
      oldSession ← get
      result ← EitherT(doUpdate(newSession, oldSession))
    } yield result

  }
}
