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

package uk.gov.hmrc.helptosavefrontend.controllers

import cats.data.EitherT
import cats.instances.future._
import cats.syntax.either._
import org.scalamock.scalatest.MockFactory
import play.api.libs.json.{JsValue, Reads, Writes}
import uk.gov.hmrc.helptosavefrontend.connectors.SessionCacheConnector
import uk.gov.hmrc.helptosavefrontend.models.HTSSession
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

trait SessionCacheBehaviourSupport { this: MockFactory ⇒

  val mockSessionCacheConnector: SessionCacheConnector = mock[SessionCacheConnector]

  def mockSessionCacheConnectorPut(expectedSession: HTSSession)(result: Either[String, Unit]): Unit =
    (mockSessionCacheConnector.put(_: HTSSession)(_: Writes[HTSSession], _: HeaderCarrier, _: ExecutionContext))
      .expects(expectedSession, *, *, *)
      .returning(EitherT.fromEither[Future](result.map(_ ⇒ CacheMap("1", Map.empty[String, JsValue]))))

  def mockSessionCacheConnectorGet(result: Either[String, Option[HTSSession]]): Unit =
    (mockSessionCacheConnector.get(_: Reads[HTSSession], _: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *)
      .returning(EitherT.fromEither[Future](result))
}
