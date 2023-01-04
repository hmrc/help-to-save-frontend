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

package uk.gov.hmrc.helptosavefrontend.controllers

import cats.data.EitherT
import cats.instances.future._
import org.scalamock.scalatest.MockFactory
import play.api.libs.json.{Reads, Writes}
import uk.gov.hmrc.helptosavefrontend.models.HTSSession
import uk.gov.hmrc.helptosavefrontend.repo.SessionStore
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait SessionStoreBehaviourSupport { this: MockFactory ⇒

  val mockSessionStore: SessionStore = mock[SessionStore]

  def mockSessionStorePut(expectedSession: HTSSession)(result: Either[String, Unit]): Unit =
    (mockSessionStore
      .store(_: HTSSession)(_: Writes[HTSSession], _: HeaderCarrier))
      .expects(expectedSession, *, *)
      .returning(EitherT.fromEither[Future](result.map(_ ⇒ ())))

  def mockSessionStoreGet(result: Either[String, Option[HTSSession]]): Unit =
    (mockSessionStore
      .get(_: Reads[HTSSession], _: HeaderCarrier))
      .expects(*, *)
      .returning(EitherT.fromEither[Future](result))
}
