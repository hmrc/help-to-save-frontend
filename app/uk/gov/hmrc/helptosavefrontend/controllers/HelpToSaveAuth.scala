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

import play.api.mvc._
import play.api.{Application, Configuration, Environment}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.~
import uk.gov.hmrc.auth.frontend.Redirects
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig.IdentityCallbackUrl
import uk.gov.hmrc.helptosavefrontend.config.{FrontendAppConfig, FrontendAuthConnector}
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.{AuthProvider, AuthWithConfidence, UserDetailsUrlWithAllEnrolments}
import uk.gov.hmrc.helptosavefrontend.models.HtsContext
import uk.gov.hmrc.helptosavefrontend.util.Logging
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

class HelpToSaveAuth(app: Application) extends FrontendController with AuthorisedFunctions with Redirects with Logging {

  override def authConnector: AuthConnector = FrontendAuthConnector

  override def config: Configuration = app.configuration

  override def env: Environment = Environment(app.path, app.classloader, app.mode)

  private type HtsAction = Request[AnyContent] ⇒ HtsContext ⇒ Future[Result]

  def authorisedForHtsWithInfo(action: Request[AnyContent] ⇒ HtsContext ⇒ Future[Result]): Action[AnyContent] =
    Action.async { implicit request ⇒
      authorised(AuthWithConfidence)
        .retrieve(UserDetailsUrlWithAllEnrolments) {
          case ~(userDetailsUri, allEnrols) ⇒
            val nino =
              allEnrols
                .enrolments
                .find(_.key == "HMRC-NI")
                .flatMap(_.getIdentifier("NINO"))
                .map(_.value)

            action(request)(HtsContext(nino, userDetailsUri, isAuthorised = true))

        }.recover {
        case e ⇒ handleFailure(e)
      }
    }

  def authorisedForHts(action: HtsAction): Action[AnyContent] = {
    Action.async { implicit request =>
      authorised(AuthProvider) {
        action(request)(HtsContext(isAuthorised = true))
      }.recover {
        case e ⇒ handleFailure(e)
      }
    }
  }

  def authorisedForHtsWithConfidence(action: HtsAction): Action[AnyContent] = {
    Action.async { implicit request =>
      authorised(AuthWithConfidence) {
        action(request)(HtsContext(isAuthorised = true))
      }.recover {
        case e ⇒ handleFailure(e)
      }
    }
  }

  def unprotected(action: HtsAction): Action[AnyContent] = {
    Action.async { implicit request =>
      authorised() {
        action(request)(HtsContext(isAuthorised = true))
      }.recoverWith {
        case _ ⇒ action(request)(HtsContext(isAuthorised = false))
      }
    }
  }

  def handleFailure(e: Throwable): Result =
    e match {
      case _: NoActiveSession ⇒ redirectToLogin
      case _: InsufficientConfidenceLevel | _: InsufficientEnrolments ⇒
        toPersonalIV(IdentityCallbackUrl, ConfidenceLevel.L200)
      case ex ⇒
        logger.error(s"could not authenticate user due to: $ex")
        InternalServerError("")
    }

  def redirectToLogin = Redirect(ggLoginUrl, Map(
    "continue" -> Seq(FrontendAppConfig.CheckEligibilityUrl),
    "accountType" -> Seq("individual"),
    "origin" -> Seq(origin)
  ))
}

