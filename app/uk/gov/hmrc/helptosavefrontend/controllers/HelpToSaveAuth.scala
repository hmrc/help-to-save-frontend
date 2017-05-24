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
import play.api.{Application, Configuration, Environment, Logger}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.frontend.Redirects
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig.{HtsUserDetailsUrl, IdentityCallbackUrl}
import uk.gov.hmrc.helptosavefrontend.config.FrontendAuthConnector
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.{HtsAuthRule, UserDetailsUrlWithAllEnrolments, AuthProvider => HtsAuthProvider}
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

case class UserDetailsUrlWithNino(path: String, nino: String)

class HelpToSaveAuth(app: Application) extends FrontendController with AuthorisedFunctions with Redirects {

  override def authConnector: AuthConnector = FrontendAuthConnector

  override def config: Configuration = app.configuration

  override def env: Environment = Environment(app.path, app.classloader, app.mode)

  private type HtsAction = Request[AnyContent] => Future[Result]
  private type HtsActionWithEnrolments = Request[AnyContent] => Option[UserDetailsUrlWithNino] => Future[Result]

  def authorisedForHtsWithEnrolments(action: HtsActionWithEnrolments): Action[AnyContent] = {
    Action.async { implicit request =>
      authorised(HtsAuthRule)
        .retrieve(UserDetailsUrlWithAllEnrolments) {
          case userDetailsUrl ~ allEnrols =>
            val nino =
              allEnrols
                .enrolments
                .find(_.key == "HMRC-NI")
                .flatMap(_.getIdentifier("NINO"))
                .map(_.value)

            val userUrlWithNino = (userDetailsUrl, nino) match {
              case (Some(url), Some(ni)) ⇒ Some(UserDetailsUrlWithNino(url, ni))
              case _ ⇒ None
            }

            action(request)(userUrlWithNino)

        }.recover {
        case e ⇒ handleFailure(e)
      }
    }
  }

  def authorisedForHts(action: HtsAction): Action[AnyContent] = {
    Action.async { implicit request =>
      authorised(HtsAuthProvider) {
        action(request)
      }.recover {
        case e ⇒ handleFailure(e)
      }
    }
  }

  def handleFailure(e: Throwable): Result =
    e match {
      case _: NoActiveSession ⇒
        toGGLogin(HtsUserDetailsUrl)
      case _: InsufficientConfidenceLevel ⇒
        toPersonalIV(IdentityCallbackUrl, ConfidenceLevel.L200)
      case ex: InternalError ⇒
        Logger.error(s"could not authenticate user due to: ${ex.reason}")
        InternalServerError("")
      case ex: AuthorisationException ⇒
        Logger.warn(s"access denied to user due to: ${ex.reason}")
        SeeOther(routes.RegisterController.accessDenied().url)
      case ex ⇒
        Logger.error(s"could not authenticate user due to: $ex")
        InternalServerError("")
    }
}

