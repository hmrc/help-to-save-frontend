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
import cats.syntax.cartesian._
import cats.syntax.option._
import org.joda.time.LocalDate
import play.api.mvc._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig._
import uk.gov.hmrc.helptosavefrontend.config.FrontendAuthConnector
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics.nanosToPrettyString
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.{AuthProvider, AuthWithCL200, UserInfoRetrievals}
import uk.gov.hmrc.helptosavefrontend.models.userinfo.{Address, MissingUserInfo, MissingUserInfos, UserInfo}
import uk.gov.hmrc.helptosavefrontend.models.{HtsContext, HtsContextWithNINO, HtsContextWithNINOAndFirstName, HtsContextWithNINOAndUserDetails}
import uk.gov.hmrc.helptosavefrontend.util.Logging._
import uk.gov.hmrc.helptosavefrontend.util.{Logging, NINO, toFuture, toJavaDate}

import scala.concurrent.Future

class HelpToSaveAuth(frontendAuthConnector: FrontendAuthConnector, metrics: Metrics)
  extends HelpToSaveFrontendController with AuthorisedFunctions with Logging {

  override def authConnector: AuthConnector = frontendAuthConnector

  private type HtsAction[A <: HtsContext] = Request[AnyContent] ⇒ A ⇒ Future[Result]

  def authorisedForHtsWithNINO(action: HtsAction[HtsContextWithNINO])(redirectOnLoginURL: String): Action[AnyContent] =
    authorised(Retrievals.nino) {
      case (mayBeNino, request, time) ⇒
        withNINO(mayBeNino, time) { nino ⇒
          action(request)(HtsContextWithNINO(authorised = true, nino))
        }(request)
    }(redirectOnLoginURL)

  def authorisedForHtsWithNINOAndName(action: HtsAction[HtsContextWithNINOAndFirstName])(redirectOnLoginURL: String): Action[AnyContent] =
    authorised(Retrievals.name and Retrievals.itmpName and Retrievals.nino){
      case (maybeName ~ maybeItmpName ~ mayBeNino, request, time) ⇒
        withNINO(mayBeNino, time){ nino ⇒
          action(request)(HtsContextWithNINOAndFirstName(authorised = true, nino, maybeName.name.orElse(maybeItmpName.givenName)))
        }(request)
    }(redirectOnLoginURL)

  def authorisedForHtsWithInfo(action: HtsAction[HtsContextWithNINOAndUserDetails])(redirectOnLoginURL: String): Action[AnyContent] =
    authorised(UserInfoRetrievals and Retrievals.nino){
      case (name ~ email ~ dateOfBirth ~ itmpName ~ itmpDateOfBirth ~ itmpAddress ~ mayBeNino, request, time) ⇒
        withNINO(mayBeNino, time){ nino ⇒
          val userDetails = getUserInfo(nino, name, email, dateOfBirth, itmpName, itmpDateOfBirth, itmpAddress)

          userDetails.fold(
            m ⇒ logger.warn(s"User details retrieval failed, missing details [${m.missingInfo.mkString(", ")}] ${timeString(time)}", nino),
            _ ⇒ logger.info(s"Successfully retrieved NINO and user details ${timeString(time)}", nino)
          )

          action(request)(HtsContextWithNINOAndUserDetails(authorised = true, nino, userDetails))
        }(request)
    }(redirectOnLoginURL)

  def authorisedForHts(action: HtsAction[HtsContext])(redirectOnLoginURL: String): Action[AnyContent] =
    authorised(EmptyRetrieval, AuthProvider){
      case (_, request, _) ⇒
        action(request)(HtsContext(authorised = true))
    }(redirectOnLoginURL)

  def authorisedForHtsWithNINOAndNoCL(action: HtsAction[HtsContextWithNINO])(redirectOnLoginURL: String): Action[AnyContent] =
    authorised(Retrievals.nino, AuthProvider) {
      case (mayBeNino, request, time) ⇒
        withNINO(mayBeNino, time) { nino ⇒
          action(request)(HtsContextWithNINO(authorised = true, nino))
        }(request)
    }(redirectOnLoginURL)

  def unprotected(action: HtsAction[HtsContext]): Action[AnyContent] =
    Action.async { implicit request ⇒
      authorised() {
        action(request)(HtsContext(authorised = true))
      }.recoverWith {
        case _ ⇒
          action(request)(HtsContext(authorised = false))
      }
    }

  private def authorised[A](retrieval: Retrieval[A],
                            predicate: Predicate    = AuthWithCL200
  )(toResult: (A, Request[AnyContent], Long) ⇒ Future[Result])(redirectOnLoginURL: ⇒ String): Action[AnyContent] =
    Action.async{ implicit request ⇒
      val timer = metrics.authTimer.time()

      authorised(predicate).retrieve(retrieval){ a ⇒
        val time = timer.stop()
        toResult(a, request, time)
      }.recover{
        val time = timer.stop()
        handleFailure(redirectOnLoginURL, time)
      }
    }

  private def withNINO[A](mayBeNino: Option[String], nanos: Long)(action: NINO ⇒ Future[Result])(implicit request: Request[_]): Future[Result] =
    mayBeNino.fold {
      logger.warn(s"NINO retrieval failed ${timeString(nanos)}")
      toFuture(internalServerError())
    }(action)

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
            UserInfo(givenName, surname, nino, toJavaDate(jodaDob), email.filter(_.nonEmpty), Address(itmpAddress))
        }

    validation
      .leftMap(m ⇒ MissingUserInfos(m.toList.toSet, nino))
      .toEither
  }

  def handleFailure(redirectOnLoginURL: String, time: Long)(implicit request: Request[_]): PartialFunction[Throwable, Result] = {
    case _: NoActiveSession ⇒
      toGGLogin(redirectOnLoginURL)

    case _: InsufficientConfidenceLevel | _: InsufficientEnrolments ⇒
      SeeOther(ivUrl(redirectOnLoginURL))

    case ex: AuthorisationException ⇒
      logger.warn(s"could not authenticate user due to: $ex ${timeString(time)}")
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
