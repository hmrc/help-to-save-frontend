/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.helptosavefrontend.auth

import java.time.LocalDateTime

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import cats.syntax.apply._
import cats.syntax.option._
import java.time.LocalDate
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.{itmpName => V2ItmpName, name => V2Name, nino => V2Nino}
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.controllers.routes
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics.nanosToPrettyString
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.{AuthProvider, AuthWithCL200, UserInfoRetrievals}
import uk.gov.hmrc.helptosavefrontend.models.userinfo.{Address, MissingUserInfo, MissingUserInfos, UserInfo}
import uk.gov.hmrc.helptosavefrontend.models.{HtsContext, HtsContextWithNINO, HtsContextWithNINOAndFirstName, HtsContextWithNINOAndUserDetails}
import uk.gov.hmrc.helptosavefrontend.util.Logging.LoggerOps
import uk.gov.hmrc.helptosavefrontend.util._
import uk.gov.hmrc.play.bootstrap.config.AuthRedirects
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.{ExecutionContext, Future}

trait HelpToSaveAuth extends AuthorisedFunctions with AuthRedirects with Logging {
  this: FrontendController =>

  val metrics: Metrics
  val appConfig: FrontendAppConfig
  implicit val transformer: NINOLogMessageTransformer
  private type HtsAction[A <: HtsContext] = Request[AnyContent] => A => Future[Result]
  private type RelativeURL = String

  def authorisedForHtsWithNINO(
    action: HtsAction[HtsContextWithNINO]
  )(
    loginContinueURL: RelativeURL
  )(implicit maintenanceSchedule: MaintenanceSchedule, ec: ExecutionContext): Action[AnyContent] =
    authorised(V2Nino) {
      case (mayBeNino, request, time) =>
        withNINO(mayBeNino, time) { nino =>
          action(request)(HtsContextWithNINO(authorised = true, nino))
        }(request)
    }(loginContinueURL)

  def authorisedForHtsWithNINOAndName(
    action: HtsAction[HtsContextWithNINOAndFirstName]
  )(
    loginContinueURL: RelativeURL
  )(implicit maintenanceSchedule: MaintenanceSchedule, ec: ExecutionContext): Action[AnyContent] =
    authorised(V2Name and V2ItmpName and V2Nino) {
      case (maybeName ~ maybeItmpName ~ mayBeNino, request, time) =>
        withNINO(mayBeNino, time) { nino =>
          val name = maybeItmpName.flatMap(_.givenName).orElse(maybeName.flatMap(_.name))
          action(request)(HtsContextWithNINOAndFirstName(authorised = true, nino, name))
        }(request)
    }(loginContinueURL)

  def authorisedForHtsWithInfo(
    action: HtsAction[HtsContextWithNINOAndUserDetails]
  )(
    loginContinueURL: RelativeURL
  )(implicit maintenanceSchedule: MaintenanceSchedule, ec: ExecutionContext): Action[AnyContent] =
    authorised(UserInfoRetrievals and V2Nino) {
      case (name ~ email ~ dateOfBirth ~ itmpName ~ itmpDateOfBirth ~ itmpAddress ~ mayBeNino, request, time) =>
        withNINO(mayBeNino, time) { nino =>
          val userDetails = getUserInfo(nino, name, email, dateOfBirth, itmpName, itmpDateOfBirth, itmpAddress)

          userDetails.fold(
            m =>
              logger.warn(
                s"User details retrieval failed, missing details [${m.missingInfo.mkString(", ")}] ${timeString(time)}",
                nino
              ),
            _ => logger.debug(s"Successfully retrieved NINO and user details ${timeString(time)}", nino)
          )

          action(request)(HtsContextWithNINOAndUserDetails(authorised = true, nino, userDetails))
        }(request)
    }(loginContinueURL)

  def authorisedForHts(
    action: HtsAction[HtsContext]
  )(
    loginContinueURL: RelativeURL
  )(implicit maintenanceSchedule: MaintenanceSchedule, ec: ExecutionContext): Action[AnyContent] =
    authorised(EmptyRetrieval, AuthProvider) {
      case (_, request, _) =>
        action(request)(HtsContext(authorised = true))
    }(loginContinueURL)

  def authorisedForHtsWithNINOAndNoCL(
    action: HtsAction[HtsContextWithNINO]
  )(
    loginContinueURL: RelativeURL
  )(implicit maintenanceSchedule: MaintenanceSchedule, ec: ExecutionContext): Action[AnyContent] =
    authorised(V2Nino, AuthProvider) {
      case (mayBeNino, request, time) =>
        withNINO(mayBeNino, time) { nino =>
          action(request)(HtsContextWithNINO(authorised = true, nino))
        }(request)
    }(loginContinueURL)

  def unprotected(action: HtsAction[HtsContext])(implicit ec: ExecutionContext): Action[AnyContent] =
    Action.async { implicit request =>
      authorised() {
        action(request)(HtsContext(authorised = true))
      }.recoverWith {
        case _ =>
          action(request)(HtsContext(authorised = false))
      }
    }

  private def authorised[A](retrieval: Retrieval[A], predicate: Predicate = AuthWithCL200)(
    toResult: (A, Request[AnyContent], Long) => Future[Result]
  )(
    loginContinueURL: => RelativeURL
  )(implicit maintenanceSchedule: MaintenanceSchedule, ec: ExecutionContext): Action[AnyContent] =
    Action.async { implicit request =>
      val timer = metrics.authTimer.time()

      authorised(predicate)
        .retrieve(retrieval) { a =>
          val time = timer.stop()
          maintenanceSchedule.endOfMaintenance() match {
            case Some(endMaintenance) => Future.failed(MaintenancePeriodException(endMaintenance))
            case None                 => toResult(a, request, time)
          }
        }
        .recover {
          val time = timer.stop()
          handleAuthFailure(loginContinueURL, time)
        }
    }

  private def withNINO[A](mayBeNino: Option[String], nanos: Long)(
    action: NINO => Future[Result]
  )(implicit request: Request[_]): Future[Result] =
    mayBeNino.fold {
      logger.warn(s"NINO retrieval failed ${timeString(nanos)}")
      toFuture(internalServerError())
    }(action)

  // need this type to be able to use the apply syntax on ValidatedNel and mapN
  private type ValidOrMissingUserInfo[A] = ValidatedNel[MissingUserInfo, A]

  private def getUserInfo(
    nino: String,
    name: Option[Name],
    email: Option[String],
    dob: Option[LocalDate],
    itmpName: Option[ItmpName],
    itmpDob: Option[LocalDate],
    itmpAddress: Option[ItmpAddress]
  ): Either[MissingUserInfos, UserInfo] = {

    val givenNameValidation: ValidOrMissingUserInfo[String] =
      itmpName
        .flatMap(_.givenName)
        .orElse(name.flatMap(_.name))
        .filter(_.nonEmpty)
        .toValidNel(MissingUserInfo.GivenName)

    val surnameValidation: ValidOrMissingUserInfo[String] =
      itmpName
        .flatMap(_.familyName)
        .orElse(name.flatMap(_.lastName))
        .filter(_.nonEmpty)
        .toValidNel(MissingUserInfo.Surname)

    val dateOfBirthValidation: ValidOrMissingUserInfo[LocalDate] =
      itmpDob
        .orElse(dob)
        .toValidNel(MissingUserInfo.DateOfBirth)

    val addressValidation: ValidOrMissingUserInfo[ItmpAddress] = {
      val missingContactDetails = Invalid(NonEmptyList.of(MissingUserInfo.Contact))

      itmpAddress.fold[ValidOrMissingUserInfo[ItmpAddress]](missingContactDetails) { a =>
        val lineCount =
          List(a.line1, a.line2, a.line3, a.line4, a.line5)
            .map(_.map(_.trim))
            .filter(_.nonEmpty)
            .collect { case Some(_) => () }
            .length

        if (lineCount < 2 || !a.postCode.exists(_.trim.nonEmpty)) {
          missingContactDetails
        } else {
          Valid(a)
        }
      }

    }

    val validation: ValidOrMissingUserInfo[UserInfo] =
      (givenNameValidation, surnameValidation, dateOfBirthValidation, addressValidation).mapN {
        case (givenName, surname, jodaDob, address) =>
          UserInfo(givenName, surname, nino, jodaDob, email.filter(_.nonEmpty), Address(address))
      }

    validation
      .leftMap(m => MissingUserInfos(m.toList.toSet, nino))
      .toEither
  }

  case class MaintenancePeriodException(endTime: LocalDateTime)
      extends AuthorisationException("MaintenancePeriodException")

  def handleAuthFailure(loginContinueURL: RelativeURL, time: Long)(
    implicit request: Request[_]
  ): PartialFunction[Throwable, Result] = {
    case _: NoActiveSession =>
      toGGLogin(loginContinueURL)

    case _: InsufficientConfidenceLevel | _: InsufficientEnrolments =>
      SeeOther(appConfig.ivUrl(loginContinueURL))

    case MaintenancePeriodException(endTime: LocalDateTime) =>
      SeeOther(routes.RegisterController.getServiceOutagePage(endTime.toString).url)

    case _: UnsupportedAuthProvider =>
      SeeOther(routes.RegisterController.getCannotCheckDetailsPage.url)

    case ex: AuthorisationException =>
      logger.warn(s"could not authenticate user due to: $ex ${timeString(time)}")
      internalServerError()
  }

  override def toGGLogin(redirectOnLoginURL: RelativeURL): Result =
    Redirect(
      appConfig.ggLoginUrl,
      Map(
        "continue_url"    -> Seq(appConfig.ggContinueUrlPrefix + redirectOnLoginURL),
        "accountType" -> Seq("individual"),
        "origin"      -> Seq(appConfig.appName)
      )
    )

  private def timeString(nanos: Long): String = s"(round-trip time: ${nanosToPrettyString(nanos)})"

  def internalServerError()(implicit request: Request[_]): Result

}
