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

import javax.inject.Singleton

import cats.data.{EitherT, ValidatedNel}
import cats.instances.future._
import cats.syntax.either._
import com.google.inject.Inject
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import play.api.{Configuration, Environment, Logger}
import uk.gov.hmrc.helptosavefrontend.connectors.NSIConnector.{SubmissionFailure, SubmissionResult, SubmissionSuccess}
import uk.gov.hmrc.helptosavefrontend.connectors._
import uk.gov.hmrc.helptosavefrontend.models.{HTSSession, NSIUserInfo, UserInfo}
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.Result
import uk.gov.hmrc.helptosavefrontend.views
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RegisterController @Inject()(val messagesApi: MessagesApi,
                                   val configuration: Configuration,
                                   val environment: Environment,
                                   htsService: HelpToSaveService,
                                   nSAndIConnector: NSIConnector,
                                   val sessionCacheConnector: SessionCacheConnector)
  extends HelpToSaveController(configuration, environment) with I18nSupport {

  def declaration: Action[AnyContent] = Action.async { implicit request ⇒
    authorisedForHts { userUrlWithNino ⇒
      validateUser(userUrlWithNino.uri, userUrlWithNino.nino).fold(
        error ⇒ {
          Logger.error(s"Could not perform eligibility check: $error")
          InternalServerError("")
        }, _.fold(
          Ok(views.html.core.not_eligible())) {
          userDetails ⇒
            sessionCacheConnector.put(HTSSession(Some(userDetails)))
            Ok(views.html.register.declaration(userDetails))
        }
      )
    }
  }

  def getCreateAccountHelpToSave: Action[AnyContent] = Action.async {
    implicit request ⇒
      authorisedForHts {
        Future.successful(Ok(views.html.register.create_account_help_to_save()))
      }
  }

  def postCreateAccountHelpToSave: Action[AnyContent] = Action.async { implicit request ⇒
    authorisedForHts {
      val submissionResult = for {
        session ← retrieveUserInfo()
        userInfo ← validateUserInfo(session)
        submissionResult ← postToNSI(userInfo)
      //todo update our backend with a boolean value letting hmrc know a hts account was created.
      } yield submissionResult

      submissionResult.value.map {
        case Right(SubmissionSuccess) ⇒
          Ok(uk.gov.hmrc.helptosavefrontend.views.html.core.stub_page("This is a stub for nsi"))
        case Right(sf: SubmissionFailure) ⇒
          Ok(uk.gov.hmrc.helptosavefrontend.views.html.core.stub_page(prettyPrintSubmissionFailure(sf)))
        case Left(error) ⇒
          Ok(uk.gov.hmrc.helptosavefrontend.views.html.core.stub_page(error))
      }
    }
  }

  private def prettyPrintSubmissionFailure(failure: SubmissionFailure): String =
    s"Submission to NSI failed: ${failure.errorMessage}: ${failure.errorDetail} (id: ${failure.errorMessageId.getOrElse("-")})"

  private def retrieveUserInfo()(implicit hc: HeaderCarrier): EitherT[Future, String, HTSSession] =
    EitherT[Future, String, HTSSession](
      sessionCacheConnector.get.map(_.fold[Either[String, HTSSession]](
        Left("Session cache did not contain user info :("))(Right.apply))
    )


  private def validateUserInfo(session: HTSSession)(implicit ex: ExecutionContext): EitherT[Future, String, NSIUserInfo] = {
    val userInfo: Option[ValidatedNel[String, NSIUserInfo]] =
      session.userInfo.map(NSIUserInfo(_))

    val nsiUserInfo: Either[String, NSIUserInfo] =
      userInfo.fold[Either[String, NSIUserInfo]](
        Left("No UserInfo In session :("))(
        _.toEither.leftMap(e ⇒ s"Invalid user details: ${e.toList.mkString(", ")}")
      )
    EitherT.fromEither[Future](nsiUserInfo)
  }

  private def postToNSI(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier): EitherT[Future, String, SubmissionResult] =
    EitherT[Future, String, SubmissionResult](nSAndIConnector.createAccount(userInfo).map(Right(_)))

  /**
    * Does the following:
    * - get the user's NINO
    * - get the user's information
    * - check's if the user is eligible for HTS
    *
    * This returns a defined [[UserInfo]] if all of the above has successfully
    * been performed and the eligibility check is positive. This returns [[None]]
    * if all the above has successfully been performed and the eligibility check is negative.
    */
  private def validateUser(userDetailsUri: String, nino: String)(implicit hc: HeaderCarrier): Result[Option[UserInfo]] = for {
    userInfo ← htsService.getUserInfo(userDetailsUri, nino)
    eligible ← htsService.checkEligibility(nino)
  } yield eligible.fold(None, Some(userInfo))

}
