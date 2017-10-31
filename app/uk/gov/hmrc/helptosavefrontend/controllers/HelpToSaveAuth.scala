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
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.Retrievals.authorisedEnrolments
import uk.gov.hmrc.auth.core.retrieve.{ItmpAddress, ItmpName, Name, ~}
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig._
import uk.gov.hmrc.helptosavefrontend.config.FrontendAuthConnector
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics.nanosToPrettyString
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.{AuthProvider, AuthWithCL200, UserRetrievals}
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.userinfo.{Address, MissingUserInfo, MissingUserInfos, UserInfo}
import uk.gov.hmrc.helptosavefrontend.util.{Logging, NINO, toFuture, toJavaDate}
import uk.gov.hmrc.helptosavefrontend.util.Logging._

import scala.concurrent.Future

class HelpToSaveAuth(frontendAuthConnector: FrontendAuthConnector, metrics: Metrics)
  extends HelpToSaveFrontendController with AuthorisedFunctions with Logging {

  override def authConnector: AuthConnector = frontendAuthConnector

  private type HtsAction[A <: HtsContext] = Request[AnyContent] ⇒ A ⇒ Future[Result]

  def authorisedForHtsWithNINO(action: HtsAction[HtsContextWithNINO])(redirectOnLoginURL: String): Action[AnyContent] =
    Action.async { implicit request ⇒
      val timer = metrics.authTimer.time()

      authorised(AuthWithCL200)
        .retrieve(authorisedEnrolments) {
          case authorisedEnrols ⇒
            val time = timer.stop()

            withNINO(authorisedEnrols.enrolments, time){ nino ⇒
              action(request)(HtsContextWithNINO(authorised = true, nino))
            }

        }.recover {
          handleFailure(redirectOnLoginURL)
        }
    }

  def authorisedForHtsWithInfo(action: HtsAction[HtsContextWithNINOAndUserDetails])(redirectOnLoginURL: String): Action[AnyContent] =
    Action.async { implicit request ⇒
      val timer = metrics.authTimer.time()

      authorised(AuthWithCL200)
        .retrieve(UserRetrievals and authorisedEnrolments) {
          case name ~ email ~ dateOfBirth ~ itmpName ~ itmpDateOfBirth ~ itmpAddress ~ authorisedEnrols ⇒
            val time = timer.stop()

            withNINO(authorisedEnrols.enrolments, time){ nino ⇒
              val userDetails = getUserInfo(nino, name, email, dateOfBirth, itmpName, itmpDateOfBirth, itmpAddress)

              userDetails.fold(
                m ⇒ logger.warn(s"User details retrieval failed, missing details [${m.missingInfo.mkString(", ")}] ${timeString(time)}", nino),
                _ ⇒ logger.info(s"Successfully retrieved NINO and user details ${timeString(time)}", nino)
              )

              action(request)(HtsContextWithNINOAndUserDetails(authorised = true, nino, userDetails))
            }
        }.recover {
          handleFailure(redirectOnLoginURL)
        }
    }

  def authorisedForHts(action: HtsAction[HtsContext])(redirectOnLoginURL: String): Action[AnyContent] = {
    Action.async { implicit request ⇒
      authorised(AuthProvider) {
        action(request)(HtsContext(authorised = true))
      }.recover {
        handleFailure(redirectOnLoginURL)
      }
    }
  }

  def unprotected(action: HtsAction[HtsContext]): Action[AnyContent] = {
    Action.async { implicit request ⇒
      authorised() {
        action(request)(HtsContext(authorised = true))
      }.recoverWith {
        case _ ⇒
          action(request)(HtsContext(authorised = false))
      }
    }
  }

  private def withNINO[A](enrolments: Set[Enrolment], nanos: Long)(withNINO: NINO ⇒ Future[Result])(implicit request: Request[_]): Future[Result] =
    enrolments
      .find(_.key === "HMRC-NI")
      .flatMap(_.getIdentifier("NINO"))
      .map(_.value)
      .fold{
        logger.warn(s"NINO retrieval failed ${timeString(nanos)}")
        toFuture(internalServerError())
      }(withNINO)

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

    val validation: ValidatedNel[MissingUserInfo, UserInfo] =
      (givenNameValidation |@| surnameValidation |@| dateOfBirthValidation)
        .map {
          case (givenName, surname, jodaDob) ⇒
            UserInfo(givenName, surname, nino, toJavaDate(jodaDob), email, Address(itmpAddress))
        }

    validation
      .leftMap(m ⇒ MissingUserInfos(m.toList.toSet, nino))
      .toEither
  }

  def handleFailure(redirectOnLoginURL: String)(implicit request: Request[_]): PartialFunction[Throwable, Result] = {
    case _: NoActiveSession ⇒
      toGGLogin(redirectOnLoginURL)

    case _: InsufficientConfidenceLevel | _: InsufficientEnrolments ⇒
      SeeOther(ivUrl(redirectOnLoginURL))

    case ex: AuthorisationException ⇒
      logger.error(s"could not authenticate user due to: $ex")
      internalServerError()
  }

  private def toGGLogin(redirectOnLoginURL: String) =
    Redirect(ggLoginUrl, Map(
      "continue" -> Seq(redirectOnLoginURL),
      "accountType" -> Seq("individual"),
      "origin" -> Seq(appName)
    ))

  private def timeString(nanos: Long): String = s"(round-trip time: ${nanosToPrettyString(nanos)})"

}
