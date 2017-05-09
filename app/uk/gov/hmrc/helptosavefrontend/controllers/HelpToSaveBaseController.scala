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
import play.api.{Configuration, Environment, Play}
import uk.gov.hmrc.auth.core.AuthProvider.{GovernmentGateway, Verify}
import uk.gov.hmrc.auth.core.Retrievals.{allEnrolments, userDetailsUri}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.frontend.Redirects
import uk.gov.hmrc.helptosavefrontend.config.FrontendAuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

class HelpToSaveBaseController extends FrontendController with AuthorisedFunctions with Redirects {

  override def authConnector: AuthConnector = FrontendAuthConnector

  val config: Configuration = Play.current.configuration
  val env: Environment = Environment(Play.current.path, Play.current.classloader, Play.current.mode)

  def authorisedForHts(callback: (String, String) => Future[Result])(implicit request: Request[AnyContent]): Future[Result] = {
    authorised(AuthProviders(GovernmentGateway, Verify)).retrieve(allEnrolments and userDetailsUri) {
      case allEnrols ~ userUri =>

        val uDetailsUri = userUri.getOrElse(throw new RuntimeException("No user details uri found for logged in user"))

        val nino = {
          allEnrols.enrolments.find(enrol ⇒ enrol.key == "HMRC-NI")
            .getOrElse(throw new RuntimeException("No HMRC-NI enrolment for logged in user"))
            .getIdentifier("NINO")
            .getOrElse(throw new RuntimeException("No NINO found for logged in user"))
            .value
        }

        callback(uDetailsUri, nino)
    } recoverWith {
      handleFailure
    }
  }

  def handleFailure(implicit request: Request[_]): PartialFunction[Throwable, Future[Result]] =
    PartialFunction[Throwable, Future[Result]] {
      case _: NoActiveSession => Future.successful(toGGLogin("/help-to-save/register/declaration"))
      case _: InsufficientConfidenceLevel => Future.successful(toPersonalIV("/help-to-save/register/declaration", ConfidenceLevel.L200))
      //    case _: AuthorisationException => //Implement
      case _ => Future.successful(InternalServerError("blah blah"))
    }

}
