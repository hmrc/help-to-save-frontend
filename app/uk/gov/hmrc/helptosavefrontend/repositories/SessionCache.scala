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

package uk.gov.hmrc.helptosavefrontend.repositories

import cats.data.EitherT
import cats.instances.future._
import cats.instances.either._
import cats.instances.option._
import cats.syntax.either._
import cats.syntax.traverse._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.Configuration
import play.api.libs.json.{JsError, Json}
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.cache.repository.CacheMongoRepository
import uk.gov.hmrc.helptosavefrontend.models.HTSSession
import uk.gov.hmrc.helptosavefrontend.util.Result
import uk.gov.hmrc.helptosavefrontend.util.JsErrorOps._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@ImplementedBy(classOf[SessionCacheImpl])
trait SessionCache {

  def store(htsSession: HTSSession)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit]

  def get()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Option[HTSSession]]

}

@Singleton
class SessionCacheImpl @Inject() (config: Configuration,
                                  mongo:  ReactiveMongoComponent)
  extends CacheMongoRepository(
    "session-cache",
    config.underlying.getInt("session-cache-expiry-time-seconds").toLong
  )(mongo.mongoConnector.db)
  with SessionCache {

  final val key: String = "htsSession"

  override def store(htsSession: HTSSession)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit] =
    for {
      id ← EitherT.fromOption[Future](hc.sessionId.map(_.value), "no session ID found")
      _ ← EitherT[Future, String, Unit]({
        createOrUpdate(id, key, Json.toJson(htsSession))
          .map[Either[String, Unit]]{ result ⇒
            if (result.writeResult.ok) {
              Right(())
            } else {
              Left(result.writeResult.errMsg.getOrElse("Unknown error"))
            }
          }
          .recover{
            case NonFatal(e) ⇒
              Left(s"Error while trying to store session: ${e.getMessage}")
          }
      })
    } yield ()

  override def get()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Option[HTSSession]] =
    for {
      id ← EitherT.fromOption[Future](hc.sessionId.map(_.value), "no session ID found")
      session ← {
        EitherT[Future, String, Option[HTSSession]](
          findById(id)
            .map{ maybeCache ⇒
              val result: Option[Either[String, HTSSession]] = maybeCache.flatMap{ cache ⇒
                cache.data.map{ jsValue ⇒
                  (jsValue \ key).toEither.fold[Either[String, HTSSession]](
                    e ⇒ Left(e.message),
                    Json.fromJson[HTSSession](_).fold[Either[String, HTSSession]](
                      e ⇒ Left(JsError(e).prettyPrint()), Right(_))
                  )
                }
              }

              result.traverse[EitherStringOr, HTSSession](identity)
            }
            .recover{
              case NonFatal(e) ⇒
                Left(s"Error while trying to read session: ${e.getMessage}")
            }
        )
      }
    } yield session

  private type EitherStringOr[A] = Either[String, A]

}
