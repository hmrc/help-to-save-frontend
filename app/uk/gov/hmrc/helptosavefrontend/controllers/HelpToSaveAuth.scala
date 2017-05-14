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

import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.helptosavefrontend.config.FrontendAuthConnector
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.{UserDetailsUrlWithAllEnrolments, AuthProvider => HtsAuthProvider}
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

case class UserDetailsUrlWithNino(path: Option[String], nino: Option[String])

trait HelpToSaveAuth extends FrontendController with AuthorisedFunctions {

  override def authConnector: AuthConnector = FrontendAuthConnector

  private type HtsAction = Request[AnyContent] => Future[Result]
  private type HtsActionWithEnrolments = Request[AnyContent] => UserDetailsUrlWithNino => Future[Result]

  def authorisedForHtsWithEnrolments(htsRule: Predicate)(action: HtsActionWithEnrolments): Action[AnyContent] = {
    Action.async { implicit request =>
      authorised(htsRule)
        .retrieve(UserDetailsUrlWithAllEnrolments) {
          case userDetailsUrl ~ allEnrols =>
            val nino =
              allEnrols.enrolments
                .find(enrolment ⇒ enrolment.key == "HMRC-NI")
                .flatMap(enrolment ⇒ enrolment.getIdentifier("NINO"))
                .map(identifier ⇒ identifier.value)

            action(request)(UserDetailsUrlWithNino(userDetailsUrl, nino))
        }
    }
  }

  def authorisedForHts(action: HtsAction): Action[AnyContent] = {
    Action.async { implicit request =>
      authorised(HtsAuthProvider) {
        action(request)
      }
    }
  }
}

