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

import play.api.mvc.Result
import play.api.{Configuration, Environment, Logger}
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.Retrievals.{allEnrolments, userDetailsUri}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.frontend.Redirects
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig.{HtsDeclarationUrl, IdentityCallbackUrl}
import uk.gov.hmrc.helptosavefrontend.config.FrontendAuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class HelpToSaveController(configuration: Configuration, environment: Environment)
  extends FrontendController with AuthorisedFunctions with Redirects {

  override def authConnector: AuthConnector = FrontendAuthConnector

  val config: Configuration = configuration
  val env: Environment = environment

  case class UserUrlWithNino(uri: String, nino: String)

  def authorisedForHts(body: UserUrlWithNino => Future[Result])(implicit hc: HeaderCarrier): Future[Result] = {

    def authorisedWithNino = authorised(Enrolment("HMRC-NI").withConfidenceLevel(ConfidenceLevel.L200))

    val compositeRetrieval = userDetailsUri and allEnrolments

    authorisedWithNino.retrieve(compositeRetrieval) {
      case userUri ~ allEnrols =>

        val nino =
          allEnrols.enrolments.find(enrol ⇒ enrol.key == "HMRC-NI")
            .flatMap(enrolment ⇒ enrolment.getIdentifier("NINO"))
            .map(ninoIdentifier ⇒ ninoIdentifier.value)

        body(UserUrlWithNino(userUri.get, nino.get))

    } recover {
      case _: NoActiveSession => toGGLogin(HtsDeclarationUrl)
      case _: InsufficientConfidenceLevel => toPersonalIV(IdentityCallbackUrl, ConfidenceLevel.L200)
      case _: AuthorisationException | _: InsufficientEnrolments => Forbidden("") //TODO
      case ex =>
        Logger.error(s"Could not perform authentication: $ex")
        InternalServerError("") //TODO
    }
  }

  def authorisedForHts(body: => Future[Result])(implicit hc: HeaderCarrier): Future[Result] = {
    authorised(AuthProviders(GovernmentGateway)) {
      body
    }
  } recover {
    case _: NoActiveSession => toGGLogin(HtsDeclarationUrl)
    case _: InsufficientConfidenceLevel => toPersonalIV(IdentityCallbackUrl, ConfidenceLevel.L200)
    case _: AuthorisationException | _: InsufficientEnrolments ⇒  Forbidden("") //TODO
    case ex =>
      Logger.error(s"Could not perform authentication: $ex")
      InternalServerError("") //TODO
  }
}

