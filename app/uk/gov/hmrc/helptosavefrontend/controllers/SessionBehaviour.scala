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

import cats.instances.future._
import play.api.mvc.{AnyContent, Request, Result}
import uk.gov.hmrc.helptosavefrontend.connectors.SessionCacheConnector
import uk.gov.hmrc.helptosavefrontend.controllers.SessionBehaviour.SessionWithEligibilityCheck
import uk.gov.hmrc.helptosavefrontend.models.HTSSession.EligibleWithUserInfo
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityCheckResult.Ineligible
import uk.gov.hmrc.helptosavefrontend.models.{HTSSession, HtsContextWithNINO}
import uk.gov.hmrc.helptosavefrontend.util.Logging._
import uk.gov.hmrc.helptosavefrontend.util.{Email, NINOLogMessageTransformer, toFuture}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

trait SessionBehaviour {
  this: BaseController ⇒

  val sessionCacheConnector: SessionCacheConnector

  def checkSession(noSession: ⇒ Future[Result])(whenSession: HTSSession ⇒ Future[Result])(
      implicit
      htsContext:  HtsContextWithNINO,
      hc:          HeaderCarrier,
      request:     Request[_],
      transformer: NINOLogMessageTransformer): Future[Result] =
    sessionCacheConnector.get
      .semiflatMap(_.fold(noSession)(whenSession))
      .leftMap{
        e ⇒
          logger.warn(s"Could not read sessions data from keystore: $e", htsContext.nino)
          internalServerError()
      }.merge

  def checkHasDoneEligibilityChecks(noSession: ⇒ Future[Result])(hasDoneChecks: SessionWithEligibilityCheck ⇒ Future[Result])(
      implicit
      htsContext:  HtsContextWithNINO,
      hc:          HeaderCarrier,
      request:     Request[AnyContent],
      transformer: NINOLogMessageTransformer): Future[Result] =
    checkSession(noSession){ session ⇒
      session.eligibilityCheckResult.fold[Future[Result]](SeeOther(routes.EligibilityCheckController.getCheckEligibility().url))(
        result ⇒ hasDoneChecks(SessionWithEligibilityCheck(result, session.pendingEmail, session.confirmedEmail)))
    }

}

object SessionBehaviour {

  case class SessionWithEligibilityCheck(eligibilityResult: Either[Ineligible, EligibleWithUserInfo],
                                         pendingEmail:      Option[Email],
                                         confirmedEmail:    Option[Email])

}
