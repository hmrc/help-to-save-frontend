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

package uk.gov.hmrc.helptosavefrontend.controllers.email

import javax.inject.Singleton

import cats.data.EitherT
import cats.instances.future._
import cats.instances.option._
import cats.instances.string._
import cats.syntax.eq._
import cats.syntax.traverse._
import com.google.inject.Inject
import play.api.Application
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import uk.gov.hmrc.helptosavefrontend.config.{FrontendAppConfig, FrontendAuthConnector}
import uk.gov.hmrc.helptosavefrontend.connectors.{EmailVerificationConnector, SessionCacheConnector}
import uk.gov.hmrc.helptosavefrontend.controllers.{EnrolmentCheckBehaviour, HelpToSaveAuth, SessionBehaviour}
import uk.gov.hmrc.helptosavefrontend.forms.UpdateEmailForm
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, EmailVerificationParams, toFuture, Result ⇒ EitherTResult}
import uk.gov.hmrc.helptosavefrontend.views
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NewApplicantUpdateEmailAddressController @Inject() (val sessionCacheConnector:      SessionCacheConnector,
                                                          val helpToSaveService:          HelpToSaveService,
                                                          frontendAuthConnector:          FrontendAuthConnector,
                                                          val emailVerificationConnector: EmailVerificationConnector
)(implicit app: Application, val messagesApi: MessagesApi, crypto: Crypto, ec: ExecutionContext)
  extends HelpToSaveAuth(app, frontendAuthConnector) with EnrolmentCheckBehaviour with SessionBehaviour with VerifyEmailBehaviour with I18nSupport {

  implicit val userType: UserType = UserType.NewApplicant

  def getUpdateYourEmailAddress: Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒
    checkIfAlreadyEnrolled { _ ⇒
      checkSession {
        SeeOther(uk.gov.hmrc.helptosavefrontend.controllers.routes.EligibilityCheckController.getCheckEligibility().url)
      } {
        _.eligibilityCheckResult.fold(
          Ok(views.html.core.not_eligible())
        )(userInfo ⇒ {
            Ok(views.html.email.update_email_address(userInfo.contactDetails.email, UpdateEmailForm.verifyEmailForm))
          })
      }
    }
  }(redirectOnLoginURL = FrontendAppConfig.checkEligibilityUrl)

  def onSubmit(): Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒
    checkIfAlreadyEnrolled { nino ⇒
      checkSession {
        SeeOther(uk.gov.hmrc.helptosavefrontend.controllers.routes.EligibilityCheckController.getCheckEligibility().url)
      } { _ ⇒
        sendEmailVerificationRequest(nino)
      }
    }
  } (redirectOnLoginURL = FrontendAppConfig.checkEligibilityUrl)

  def emailVerified(emailVerificationParams: String): Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒
    handleEmailVerified(
      emailVerificationParams,
      { params ⇒
        val result: EitherT[Future, String, Option[NSIUserInfo]] = for {
          session ← sessionCacheConnector.get
          userInfo ← updateSession(session, params)
        } yield userInfo

        result.fold({
          e ⇒
            logger.warn(e)
            InternalServerError
        }, { maybeNSIUserInfo ⇒
          maybeNSIUserInfo.fold{
            // this means they were ineligible
            Ok(views.html.core.not_eligible())
          }{ updatedNSIUserInfo ⇒
            Ok(views.html.register.confirm_details(updatedNSIUserInfo))
          }
        })
      })
  } (redirectOnLoginURL = FrontendAppConfig.checkEligibilityUrl)

  /** Return `None` if user is ineligible */
  private def getEligibleUserInfo(session: Option[HTSSession])(implicit htsContext: HtsContext): Either[String, Option[NSIUserInfo]] = session match {

    case Some(s) ⇒
      s.eligibilityCheckResult.fold[Either[String, Option[NSIUserInfo]]](
        // IMPOSSIBLE - this means they are ineligible
        Right(None)
      ) { userInfo ⇒ Right(Some(userInfo)) }

    case None ⇒
      htsContext.userDetails
        .fold[Either[String, Option[NSIUserInfo]]](
          Left("No user info for user found")
        ) {
            _.fold[Either[String, Option[NSIUserInfo]]](
              missingInfos ⇒ Left(s"Missing user info: ${missingInfos.missingInfo}"),
              nsiUserInfo ⇒ Right(Some(nsiUserInfo))
            )
          }
  }

  /** Return `None` if user is ineligible */
  private def updateSession(session: Option[HTSSession],
                            params:  EmailVerificationParams)(
      implicit
      htsContext: HtsContext,
      hc:         HeaderCarrier): EitherT[Future, String, Option[NSIUserInfo]] = {
    EitherT.fromEither[Future](getEligibleUserInfo(session)).flatMap { maybeInfo ⇒
      val updatedInfo: Option[EitherT[Future, String, NSIUserInfo]] = maybeInfo.map{ info ⇒
        if (info.nino =!= params.nino) {
          EitherT.fromEither[Future](Left[String, NSIUserInfo]("NINO in confirm details parameters did not match NINO from auth"))
        } else {
          val newInfo = info.updateEmail(params.email)
          val newSession = HTSSession(Some(newInfo), None)
          sessionCacheConnector.put(newSession).map(_ ⇒ newInfo)
        }
      }

      updatedInfo.traverse[EitherTResult, NSIUserInfo](identity)
    }
  }

}

