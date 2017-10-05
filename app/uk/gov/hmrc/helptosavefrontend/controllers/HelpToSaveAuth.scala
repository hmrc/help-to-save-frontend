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
import cats.syntax.either._
import cats.syntax.eq._
import cats.syntax.option._
import org.joda.time.LocalDate
import play.api.mvc._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.Retrievals.authorisedEnrolments
import uk.gov.hmrc.auth.core.retrieve.{ItmpAddress, ItmpName, Name, ~}
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig._
import uk.gov.hmrc.helptosavefrontend.config.FrontendAuthConnector
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics.nanosToPrettyString
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.{AuthProvider, AuthWithCL200, UserRetrievals}
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.util.{Logging, toFuture, toJavaDate}
import uk.gov.hmrc.helptosavefrontend.util.Logging._
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

class HelpToSaveAuth(frontendAuthConnector: FrontendAuthConnector, metrics: Metrics)
  extends FrontendController with AuthorisedFunctions with Logging {

  override def authConnector: AuthConnector = frontendAuthConnector

  private type HtsAction = Request[AnyContent] ⇒ HtsContext ⇒ Future[Result]

  def authorisedForHtsWithInfo(action: Request[AnyContent] ⇒ HtsContextWithNINO ⇒ Future[Result])(redirectOnLoginURL: String): Action[AnyContent] =
    Action.async { implicit request ⇒
      val timer = metrics.authTimer.time()

      authorised(AuthWithCL200)
        .retrieve(UserRetrievals and authorisedEnrolments) {
          case name ~ email ~ dateOfBirth ~ itmpName ~ itmpDateOfBirth ~ itmpAddress ~ authorisedEnrols ⇒
            val time = timer.stop()
            val timeString = s"(time: ${nanosToPrettyString(time)})"

            val mayBeNino = authorisedEnrols.enrolments
              .find(_.key === "HMRC-NI")
              .flatMap(_.getIdentifier("NINO"))
              .map(_.value)

            mayBeNino.fold{
              logger.warn(s"Could not find NINO for user $timeString")
              toFuture(InternalServerError("could not find NINO for logged in user"))
            }(nino ⇒ {
              val userDetails = getUserInfo(nino, name, email, dateOfBirth, itmpName, itmpDateOfBirth, itmpAddress)

              userDetails.fold(
                m ⇒ logger.warn(s"Could not find user info, missing details [${m.missingInfo.mkString(", ")}] $timeString", nino),
                _ ⇒ logger.info(s"Successfully retrieved NINO and usr details $timeString", nino)
              )

              action(request)(HtsContextWithNINO(nino, userDetails.map(NSIUserInfo.apply), isAuthorised = true))
            })

        }.recover {
          handleFailure(redirectOnLoginURL)
        }
    }

  def authorisedForHts(action: HtsAction)(redirectOnLoginURL: String): Action[AnyContent] = {
    Action.async { implicit request ⇒
      authorised(AuthProvider) {
        action(request)(HtsContext(authorised = true))
      }.recover {
        handleFailure(redirectOnLoginURL)
      }
    }
  }

  def authorisedForHtsWithCL200(action: HtsAction)(redirectOnLoginURL: String): Action[AnyContent] = {
    Action.async { implicit request ⇒
      authorised(AuthWithCL200) {
        action(request)(HtsContext(authorised = true))
      }.recover {
        handleFailure(redirectOnLoginURL)
      }
    }
  }

  def unprotected(action: HtsAction): Action[AnyContent] = {
    Action.async { implicit request ⇒
      authorised() {
        action(request)(HtsContext(authorised = true))
      }.recoverWith {
        case _ ⇒
          action(request)(HtsContext(authorised = false))
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
        .map {
          case (givenName, surname, jodaDob, emailAddress) ⇒
            UserInfo(givenName, surname, nino, toJavaDate(jodaDob), emailAddress, Address(itmpAddress))
        }

    validation
      .leftMap(m ⇒ MissingUserInfos(m.toList.toSet, nino))
      .toEither
  }

  def handleFailure(redirectOnLoginURL: String): PartialFunction[Throwable, Result] = {
    case _: NoActiveSession ⇒
      toGGLogin(redirectOnLoginURL)

    case _: InsufficientConfidenceLevel | _: InsufficientEnrolments ⇒
      SeeOther(IvUrl)

    case ex: AuthorisationException ⇒
      logger.error(s"could not authenticate user due to: $ex")
      InternalServerError("")
  }

  private def toGGLogin(redirectOnLoginURL: String) =
    Redirect(ggLoginUrl, Map(
      "continue" -> Seq(redirectOnLoginURL),
      "accountType" -> Seq("individual"),
      "origin" -> Seq(origin)
    ))

}

