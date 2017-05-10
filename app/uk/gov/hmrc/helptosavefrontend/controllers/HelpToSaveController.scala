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

import play.api.mvc.{AnyContent, Request, Result}
import play.api.{Configuration, Environment, Logger, Play}
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.Retrievals.{allEnrolments, userDetailsUri}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.frontend.Redirects
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig.HtsDeclarationUrl
import uk.gov.hmrc.helptosavefrontend.config.FrontendAuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

class HelpToSaveController extends FrontendController with AuthorisedFunctions with Redirects {

  override def authConnector: AuthConnector = FrontendAuthConnector

  val config: Configuration = Play.current.configuration
  val env: Environment = Environment(Play.current.path, Play.current.classloader, Play.current.mode)

  case class HelpToSaveException(message: String) extends RuntimeException(message)

  def authorisedForHts(body: (String, String) => Future[Result])(implicit request: Request[AnyContent]): Future[Result] = {

    def authorisedWithNino = authorised(Enrolment("HMRC-NI").withConfidenceLevel(ConfidenceLevel.L200))
    val compositeRetrieval = userDetailsUri and allEnrolments

    authorisedWithNino.retrieve(compositeRetrieval) {
      case userUri ~ allEnrols =>

        val nino =
          allEnrols.enrolments.find(enrol ⇒ enrol.key == "HMRC-NI")
            .getOrElse(throw HelpToSaveException("No HMRC-NI enrolment found for logged in user"))
            .getIdentifier("NINO")
            .getOrElse(throw HelpToSaveException("No NINO found for logged in user"))
            .value

        body(userUri.get, nino)

    } recoverWith {
      handleFailure
    }
  }

  def authorisedForHts(body: => Future[Result])(implicit request: Request[AnyContent]): Future[Result] = {
    authorised(AuthProviders(GovernmentGateway)) {
      body
    } recoverWith {
      handleFailure
    }
  }

  def handleFailure(implicit request: Request[_]): PartialFunction[Throwable, Future[Result]] =
    PartialFunction[Throwable, Future[Result]] {
      case _: NoActiveSession => Future.successful(toGGLogin(HtsDeclarationUrl))
      case _: InsufficientConfidenceLevel => Future.successful(toPersonalIV(HtsDeclarationUrl, ConfidenceLevel.L200))
      //    case _: AuthorisationException => //Implement
      case ex =>
        Logger.error(s"Could not perform authentication: $ex")
        Future.successful(InternalServerError(""))
    }
}

