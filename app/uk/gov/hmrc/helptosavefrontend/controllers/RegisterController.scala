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

import cats.data.EitherT
import cats.instances.future._
import cats.instances.option._
import cats.syntax.traverse._
import com.google.inject.Inject
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import play.api.{Application, Logger}
import uk.gov.hmrc.helptosavefrontend.connectors.CreateAccountConnector.{SubmissionFailure, SubmissionResult, SubmissionSuccess}
import uk.gov.hmrc.helptosavefrontend.connectors._
import uk.gov.hmrc.helptosavefrontend.models.{HTSSession, UserInfo}
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.Result
import uk.gov.hmrc.helptosavefrontend.views
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

@Singleton
class RegisterController @Inject()(override val messagesApi: MessagesApi,
                                   htsService: HelpToSaveService,
                                   nSAndIConnector: CreateAccountConnector,
                                   val sessionCacheConnector: SessionCacheConnector,
                                   implicit val app: Application)
  extends HelpToSaveAuth(app) with I18nSupport {

  def declaration: Action[AnyContent] = authorisedForHtsWithEnrolments {
    implicit request ⇒
      implicit userUrlWithNino ⇒
        val userInfo = for {
          userUrlWithNino ← EitherT.fromOption[Future](userUrlWithNino, "could not retrieve either userDetailsUrl or NINO from auth")
          result ← checkEligibility(userUrlWithNino.path, userUrlWithNino.nino)
          _ ← writeToKeyStore(result)
        } yield result

        userInfo.fold(
          error ⇒ {
            Logger.error(s"Could not perform eligibility check: $error")
            InternalServerError("")
          }, _.fold(
            Ok(views.html.core.not_eligible()))(
            userDetails ⇒ Ok(views.html.register.declaration(userDetails)))
        )
  }

  def getCreateAccountHelpToSave: Action[AnyContent] = authorisedForHts {
    implicit request ⇒
      Future.successful(Ok(views.html.register.create_account_help_to_save()))
  }

  def postCreateAccountHelpToSave: Action[AnyContent] = authorisedForHts {
    implicit request ⇒
      val submissionResult = for {
        userInfo ← retrieveUserInfo()
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

  def accessDenied: Action[AnyContent] = Action.async {
    implicit request ⇒
      Future.successful(Ok(views.html.access_denied()))
  }

  private def prettyPrintSubmissionFailure(failure: SubmissionFailure): String =
    s"Submission to NSI failed: ${failure.errorMessage}: ${failure.errorDetail} (id: ${failure.errorMessageId.getOrElse("-")})"


  private def retrieveUserInfo()(implicit hc: HeaderCarrier): EitherT[Future, String, UserInfo] = {
    val session = sessionCacheConnector.get
    val userInfo = session.map(_.flatMap(_.userInfo))

    EitherT(
      userInfo.map(_.fold[Either[String, UserInfo]](
        Left("Session cache did not contain session data"))(Right(_))))
  }

  /**
    * Writes the user info to key-store if it exists and returns the associated [[CacheMap]]. If the user info
    * is not defined, don't do anything and return [[None]]. Any errors during writing to key-store are
    * captured as a [[String]] in the [[Either]].
    */
  def writeToKeyStore(userDetails: Option[UserInfo])(implicit hc: HeaderCarrier): EitherT[Future, String, Option[CacheMap]] = {
    // write to key-store
    val cacheMapOption: Option[Future[CacheMap]] =
      userDetails.map { details ⇒ sessionCacheConnector.put(HTSSession(Some(details))) }

    // use traverse to swap the option and future
    val cacheMapFuture: Future[Option[CacheMap]] =
      cacheMapOption.traverse[Future, CacheMap](identity)

    EitherT(
      cacheMapFuture.map[Either[String, Option[CacheMap]]](Right(_))
        .recover { case e ⇒ Left(s"Could not write to key-store: ${e.getMessage}") }
    )
  }

  private def postToNSI(userInfo: UserInfo)(implicit hc: HeaderCarrier): EitherT[Future, String, SubmissionResult] =
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
  private def checkEligibility(userDetailsUri: String, nino: String)(implicit hc: HeaderCarrier): Result[Option[UserInfo]] =
    for {
      userInfo ← htsService.getUserInfo(userDetailsUri, nino)
      eligible ← htsService.checkEligibility(nino)
    } yield eligible.fold(None, Some(userInfo))
}
