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

import cats.data.ValidatedNel
import cats.instances.string._
import cats.syntax.cartesian._
import cats.syntax.eq._
import cats.syntax.option._
import org.joda.time.LocalDate
import play.api.mvc._
import play.api.{Application, Configuration, Environment}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Retrievals.authorisedEnrolments
import uk.gov.hmrc.auth.core.retrieve.{ItmpAddress, ItmpName, Name, ~}
import uk.gov.hmrc.auth.frontend.Redirects
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig.{encoded, identityCallbackUrl}
import uk.gov.hmrc.helptosavefrontend.config.FrontendAuthConnector
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.{AuthProvider, AuthWithCL200, UserRetrievals}
import uk.gov.hmrc.helptosavefrontend.models.MissingUserInfos
import uk.gov.hmrc.helptosavefrontend.models.{Address, HtsContext, MissingUserInfo, UserInfo}
import uk.gov.hmrc.helptosavefrontend.util.{Logging, toFuture, toJavaDate}
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

class HelpToSaveAuth(app: Application, frontendAuthConnector: FrontendAuthConnector)
  extends FrontendController with AuthorisedFunctions with Redirects with Logging {

  override def authConnector: AuthConnector = frontendAuthConnector

  override def config: Configuration = app.configuration

  override def env: Environment = Environment(app.path, app.classloader, app.mode)

  private type HtsAction = Request[AnyContent] ⇒ HtsContext ⇒ Future[Result]

  def authorisedForHtsWithInfo(action: Request[AnyContent] ⇒ HtsContext ⇒ Future[Result])(redirectOnLoginURL: String): Action[AnyContent] =
    Action.async { implicit request ⇒
      authorised(AuthWithCL200)
        .retrieve(UserRetrievals and authorisedEnrolments) {
          case name ~ email ~ dateOfBirth ~ itmpName ~ itmpDateOfBirth ~ itmpAddress ~ authorisedEnrols ⇒

            val mayBeNino = authorisedEnrols.enrolments
              .find(_.key === "HMRC-NI")
              .flatMap(_.getIdentifier("NINO"))
              .map(_.value)

            mayBeNino.fold(
              toFuture(InternalServerError("could not find NINO for logged in user"))
            )(nino ⇒ {
                val userDetails = getUserInfo(nino, name, email, dateOfBirth, itmpName, itmpDateOfBirth, itmpAddress)
                action(request)(HtsContext(Some(nino), Some(userDetails), isAuthorised = true))
              })

        }.recover {
          handleFailure(redirectOnLoginURL)
        }
    }

  def authorisedForHts(action: HtsAction)(redirectOnLoginURL: String): Action[AnyContent] = {
    Action.async { implicit request ⇒
      authorised(AuthProvider) {
        action(request)(HtsContext(isAuthorised = true))
      }.recover {
        handleFailure(redirectOnLoginURL)
      }
    }
  }

  def authorisedForHtsWithCL200(action: HtsAction)(redirectOnLoginURL: String): Action[AnyContent] = {
    Action.async { implicit request ⇒
      authorised(AuthWithCL200) {
        action(request)(HtsContext(isAuthorised = true))
      }.recover {
        handleFailure(redirectOnLoginURL)
      }
    }
  }

  def unprotected(action: HtsAction): Action[AnyContent] = {
    Action.async { implicit request ⇒
      authorised() {
        action(request)(HtsContext(isAuthorised = true))
      }.recoverWith {
        case _ ⇒
          action(request)(HtsContext(isAuthorised = false))
      }
    }
  }

  private def getUserInfo(nino:        String,
                          name:        Name,
                          email:       Option[String],
                          dob:         Option[LocalDate],
                          itmpName:    ItmpName,
                          itmpDob:     Option[LocalDate],
                          itmpAddress: ItmpAddress): Either[MissingUserInfos, UserInfo] = {

    val givenNameValidation: ValidatedNel[MissingUserInfo, String] =
      itmpName.givenName.orElse(name.name)
        .toValidNel(MissingUserInfo.GivenName)

    val surnameValidation =
      itmpName.familyName.orElse(name.lastName)
        .toValidNel(MissingUserInfo.Surname)

    val dateOfBirthValidation =
      itmpDob.orElse(dob)
        .toValidNel(MissingUserInfo.DateOfBirth)

    val emailValidation = email.toValidNel(MissingUserInfo.Email)

    val validation: ValidatedNel[MissingUserInfo, UserInfo] =
      (givenNameValidation |@| surnameValidation |@| dateOfBirthValidation |@| emailValidation)
        .map((givenName, surname, jodaDob, email) ⇒
          UserInfo(givenName, surname, nino, jodaDob, email, Address(itmpAddress)))

    validation
      .leftMap(m ⇒ MissingUserInfos(m.toList.toSet, nino))
      .toEither
  }

  def handleFailure(redirectOnLoginURL: String): PartialFunction[Throwable, Result] = {
    case _: NoActiveSession ⇒
      redirectToLogin(redirectOnLoginURL)

    case _: InsufficientConfidenceLevel | _: InsufficientEnrolments ⇒
      toPersonalIV(s"$identityCallbackUrl?continueURL=${encoded(redirectOnLoginURL)}", ConfidenceLevel.L200)

    case ex: AuthorisationException ⇒
      logger.error(s"could not authenticate user due to: $ex")
      InternalServerError("")
  }

  private def redirectToLogin(redirectOnLoginURL: String) = Redirect(ggLoginUrl, Map(
    "continue" -> Seq(redirectOnLoginURL),
    "accountType" -> Seq("individual"),
    "origin" -> Seq(origin)
  ))
}

