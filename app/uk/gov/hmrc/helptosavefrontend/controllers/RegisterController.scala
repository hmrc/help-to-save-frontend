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
import cats.syntax.either._
import cats.syntax.traverse._
import com.google.inject.Inject
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Result}
import play.api.{Application, Logger}
import uk.gov.hmrc.helptosavefrontend.connectors.NSIConnector.SubmissionFailure
import uk.gov.hmrc.helptosavefrontend.connectors._
import uk.gov.hmrc.helptosavefrontend.models.{HTSSession, NSIUserInfo, UserInfo}
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.FEATURE
import uk.gov.hmrc.helptosavefrontend.views
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RegisterController @Inject()(val messagesApi: MessagesApi,
                                   helpToSaveService: HelpToSaveService,
                                   sessionCacheConnector: SessionCacheConnector)(implicit app: Application, ec: ExecutionContext)
  extends HelpToSaveAuth(app) with I18nSupport {

  def confirmDetails: Action[AnyContent] = authorisedForHtsWithEnrolments {
    implicit request ⇒
      implicit userUrlWithNino ⇒
        val userInfo = for {
          userUrlWithNino ← EitherT.fromOption[Future](userUrlWithNino, "could not retrieve either userDetailsUrl or NINO from auth")
          eligible ← helpToSaveService.checkEligibility(userUrlWithNino.nino, userUrlWithNino.path)
          _ ← writeToKeyStore(eligible.result)
        } yield eligible

        userInfo.fold(
          error ⇒ {
            Logger.error(s"Could not perform eligibility check: $error")
            InternalServerError("")
          }, _.result.fold(
            SeeOther(routes.RegisterController.notEligible().url))(
            userDetails ⇒ Ok(views.html.register.confirm_details(userDetails)))
        )
  }

  def getCreateAccountHelpToSavePage: Action[AnyContent] = authorisedForHtsWithConfidence {
    implicit request ⇒
      Future.successful(Ok(views.html.register.create_account_help_to_save()))
  }

  def createAccountHelpToSave: Action[AnyContent] = authorisedForHtsWithConfidence {
    implicit request ⇒
      val result = for {
        userInfo    ← retrieveUserInfo()
        nsiUserInfo ← toNSIUserInfo(userInfo)
        _ ← helpToSaveService.createAccount(nsiUserInfo).leftMap(submissionFailureToString)
      } yield userInfo

      // TODO: plug in actual pages below
      result.fold(
        error ⇒ {
          Logger.error(s"Could not create account: $error")
          Ok(uk.gov.hmrc.helptosavefrontend.views.html.core.stub_page(s"Account creation failed: $error"))
        },
        info ⇒ {
          Logger.debug(s"Successfully created account for ${info.NINO}")
          Ok(uk.gov.hmrc.helptosavefrontend.views.html.core.stub_page("Successfully created account"))
        }
      )
  }

  def accessDenied: Action[AnyContent] = Action.async {
    implicit request ⇒
      Future.successful(Ok(views.html.access_denied()))
  }

  val notEligible: Action[AnyContent] = Action.async { implicit request ⇒
    Future.successful(Ok(views.html.core.not_eligible()))
  }

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
  private def writeToKeyStore(userDetails: Option[UserInfo])(implicit hc: HeaderCarrier): EitherT[Future, String, Option[CacheMap]] = {
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

  private def toNSIUserInfo(userInfo: UserInfo): EitherT[Future,String,NSIUserInfo] =
    EitherT.fromEither[Future](NSIUserInfo(userInfo)
      .toEither
      .leftMap(errors ⇒ s"User info validation failed: ${errors.toList.mkString(", ")}"))

  private def submissionFailureToString(failure: SubmissionFailure): String =
    s"Call to NS&I failed: message ID was ${failure.errorMessageId.getOrElse("-")}, "+
      s"error was ${failure.errorMessage}, error detail was ${failure.errorDetail}}"



}
