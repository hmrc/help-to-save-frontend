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

package uk.gov.hmrc.helptosavefrontend.controllers

import cats.instances.future._
import play.api.mvc.{Request, Result}
import uk.gov.hmrc.helptosavefrontend.connectors.SessionCacheConnector
import uk.gov.hmrc.helptosavefrontend.models.{HTSSession, HtsContextWithNINO}
import uk.gov.hmrc.helptosavefrontend.util.Logging
import uk.gov.hmrc.helptosavefrontend.util.Logging._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

trait SessionBehaviour {
  this: HelpToSaveFrontendController with Logging ⇒

  val sessionCacheConnector: SessionCacheConnector

  def checkSession(noSession: ⇒ Future[Result])(whenSession: HTSSession ⇒ Future[Result])(
      implicit
      htsContext: HtsContextWithNINO, hc: HeaderCarrier, request: Request[_]): Future[Result] = {
    sessionCacheConnector.get.fold({
      e ⇒
        logger.warn(s"Could not read sessions data from keystore: $e", htsContext.nino)
        Future.successful(InternalServerError(internalServerError()))
    }, _.fold(noSession)(whenSession)
    ).flatMap(identity)

  }

}
