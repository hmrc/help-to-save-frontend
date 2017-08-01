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
import com.google.inject.Inject
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import play.api.Application
import uk.gov.hmrc.helptosavefrontend.connectors.NSIConnector.SubmissionFailure
import uk.gov.hmrc.helptosavefrontend.connectors._
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.services.{EnrolmentService, HelpToSaveService}
import uk.gov.hmrc.helptosavefrontend.util.Logging
import uk.gov.hmrc.helptosavefrontend.views
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RegisterController @Inject()(val messagesApi: MessagesApi,
                                   helpToSaveService: HelpToSaveService,
                                   sessionCacheConnector: SessionCacheConnector,
                                   enrolmentService: EnrolmentService,
                                   val app: Application)(implicit ec: ExecutionContext)
  extends HelpToSaveAuth(app) with I18nSupport with Logging {


  def getCreateAccountHelpToSavePage: Action[AnyContent] = authorisedForHtsWithConfidence {
    implicit request ⇒
      implicit htsContext ⇒
        Future.successful(Ok(views.html.register.create_account_help_to_save()))
  }

  def createAccountHelpToSave: Action[AnyContent] = authorisedForHtsWithConfidence {
    implicit request ⇒
      implicit htsContext ⇒
        val result = for {
          userInfo ← retrieveUserInfo()
          _        ← helpToSaveService.createAccount(userInfo).leftMap(submissionFailureToString)
        } yield userInfo

        // TODO: plug in actual pages below
        result.fold(
          error ⇒ {
            // TODO: error or warning?
            logger.error(s"Could not create account: $error")
            Ok(uk.gov.hmrc.helptosavefrontend.views.html.core.stub_page(s"Account creation failed: $error"))
          },
          info ⇒ {
            logger.info(s"Successfully created account for ${info.nino}")
            // start the process to enrol the user but don't worry about the result
            enrolmentService.enrolUser(info.nino).fold(
              e ⇒ logger.warn(s"Could not start process to enrol user ${info.nino}: $e"),
              _ ⇒ logger.info(s"Started process to enrol user ${info.nino}")
            )

            Ok(uk.gov.hmrc.helptosavefrontend.views.html.core.stub_page("Successfully created account"))
          }
        )
  }

  private def submissionFailureToString(failure: SubmissionFailure): String =
    s"Call to NS&I failed: message ID was ${failure.errorMessageId.getOrElse("-")},  " +
      s"error was ${failure.errorMessage}, error detail was ${failure.errorDetail}}"

  private def retrieveUserInfo()(implicit hc: HeaderCarrier): EitherT[Future, String, NSIUserInfo] = {
    val session = sessionCacheConnector.get
    val userInfo: Future[Option[NSIUserInfo]] = session.map(_.flatMap(_.userInfo))

    EitherT(
      userInfo.map(_.fold[Either[String, NSIUserInfo]](
        Left("Session cache did not contain session data"))(Right(_))))
  }

}

