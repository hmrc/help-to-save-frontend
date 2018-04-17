/*
 * Copyright 2018 HM Revenue & Customs
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

import cats.data.EitherT
import cats.instances.future._
import cats.instances.string._
import cats.syntax.either._
import cats.syntax.eq._
import com.google.inject.Inject
import javax.inject.Singleton
import play.api.i18n.MessagesApi
import play.api.mvc._
import play.api.{Application, Configuration, Environment}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.auth.HelpToSaveAuth
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.connectors.{EmailVerificationConnector, SessionCacheConnector}
import uk.gov.hmrc.helptosavefrontend.forms.EmailVerificationErrorContinueForm
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.models.HTSSession.EligibleWithUserInfo
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityCheckResult.{AlreadyHasAccount, Eligible, Ineligible}
import uk.gov.hmrc.helptosavefrontend.models.userinfo.UserInfo
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.Logging._
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, Email, EmailVerificationParams, NINOLogMessageTransformer, toFuture, Result ⇒ EitherTResult}
import uk.gov.hmrc.helptosavefrontend.views
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.ActionWithMdc

import scala.concurrent.Future

@Singleton
class NewApplicantUpdateEmailAddressController @Inject() (val sessionCacheConnector:      SessionCacheConnector,
                                                          val helpToSaveService:          HelpToSaveService,
                                                          val authConnector:              AuthConnector,
                                                          val emailVerificationConnector: EmailVerificationConnector,
                                                          val metrics:                    Metrics,
                                                          val auditor:                    HTSAuditor)(implicit app: Application,
                                                                                                      override val messagesApi: MessagesApi,
                                                                                                      crypto:                   Crypto,
                                                                                                      val transformer:          NINOLogMessageTransformer,
                                                                                                      val frontendAppConfig:    FrontendAppConfig,
                                                                                                      val config:               Configuration,
                                                                                                      val env:                  Environment)
  extends BaseController with HelpToSaveAuth with EnrolmentCheckBehaviour with SessionBehaviour with VerifyEmailBehaviour {

  private def checkEnrolledAndSession(ifEligible: (UserInfo, Option[Email], Option[Email]) ⇒ Future[Result])(implicit request: Request[AnyContent],
                                                                                                             htsContext: HtsContextWithNINO): Future[Result] =
    checkIfAlreadyEnrolled { () ⇒
      checkHasDoneEligibilityChecks {
        SeeOther(routes.EligibilityCheckController.getCheckEligibility().url)
      } { session ⇒
        session.eligibilityResult.fold(
          _ ⇒ SeeOther(routes.EligibilityCheckController.getIsNotEligible().url),
          info ⇒ ifEligible(info.userInfo, session.pendingEmail, session.confirmedEmail)
        )
      }
    }

  def verifyEmail: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    checkEnrolledAndSession {
      case (userInfo, maybePendingEmail, _) ⇒
        maybePendingEmail.fold[Future[Result]](
          internalServerError()
        ) { pendingEmail ⇒
            sendEmailVerificationRequest(
              pendingEmail,
              userInfo.forename,
              Ok(views.html.register.check_your_email(pendingEmail, userInfo.email)),
              params ⇒ routes.NewApplicantUpdateEmailAddressController.emailVerifiedCallback(params.encode()).url,
              _ ⇒ SeeOther(userInfo.email.fold(
                routes.EmailVerificationErrorController.verifyEmailErrorTryLater().url)(
                  _ ⇒ routes.NewApplicantUpdateEmailAddressController.verifyEmailError().url)),
              isNewApplicant = true)
          }
    }
  }(redirectOnLoginURL = routes.NewApplicantUpdateEmailAddressController.verifyEmail.url)

  def verifyEmailError: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    checkEnrolledAndSession {
      case (info, _, _) ⇒
        info.email.fold(
          SeeOther(routes.EmailVerificationErrorController.verifyEmailErrorTryLater().url)
        )(email ⇒ Ok(views.html.register.cannot_change_email(email, EmailVerificationErrorContinueForm.continueForm)))
    }
  }(redirectOnLoginURL = routes.NewApplicantUpdateEmailAddressController.verifyEmailError().url)

  def verifyEmailErrorSubmit: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    checkEnrolledAndSession {
      case (info, _, _) ⇒
        info.email.fold(
          SeeOther(routes.EmailVerificationErrorController.verifyEmailErrorTryLater().url)
        )(email ⇒
            EmailVerificationErrorContinueForm.continueForm.bindFromRequest().fold(
              form ⇒ Ok(views.html.register.cannot_change_email(email, form)),
              { continue ⇒
                if (continue.value) {
                  SeeOther(routes.RegisterController.confirmEmail(crypto.encrypt(email)).url)
                } else {
                  SeeOther(routes.IntroductionController.getAboutHelpToSave().url)
                }
              }
            ))
    }
  }(redirectOnLoginURL = routes.NewApplicantUpdateEmailAddressController.verifyEmailError().url)

  def emailVerifiedCallback(emailVerificationParams: String): Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒
    handleEmailVerified(
      emailVerificationParams,
      { params ⇒
        val result: EitherT[Future, String, Either[Ineligible, UserInfo]] = for {
          session ← sessionCacheConnector.get
          userInfo ← updateSession(session, params)
        } yield userInfo

        result.fold({
          e ⇒
            logger.warn(e)
            internalServerError()
        }, { maybeNSIUserInfo ⇒
          maybeNSIUserInfo.fold(
            _ ⇒ SeeOther(routes.EligibilityCheckController.getIsNotEligible().url),
            _ ⇒ SeeOther(routes.NewApplicantUpdateEmailAddressController.getEmailVerified().url)
          )
        })
      },
      toFuture(SeeOther(routes.EmailVerificationErrorController.verifyEmailErrorTryLater().url))
    )
  }(redirectOnLoginURL = routes.NewApplicantUpdateEmailAddressController.emailVerifiedCallback(emailVerificationParams).url)

  def getEmailVerified: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    checkEnrolledAndSession {
      case (info, _, confirmedEmail) ⇒
        confirmedEmail.fold[Future[Result]] {
          logger.warn("Could not find confirmed email", htsContext.nino)
          val url = info.email.fold(
            routes.EmailVerificationErrorController.verifyEmailErrorTryLater().url)(
              _ ⇒ routes.NewApplicantUpdateEmailAddressController.verifyEmailError().url
            )
          toFuture(SeeOther(url))
        }(email ⇒ toFuture(Ok(views.html.register.email_updated(email))))
    }
  }(redirectOnLoginURL = routes.NewApplicantUpdateEmailAddressController.getEmailVerified().url)

  def getEmailUpdated(): Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    checkEnrolledAndSession {
      case (info, _, _) ⇒
        info.email.fold(
          SeeOther(routes.RegisterController.getGiveEmailPage().url))(
            email ⇒ Ok(views.html.register.email_updated(email)))
    }
  }(redirectOnLoginURL = frontendAppConfig.checkEligibilityUrl)

  def emailUpdatedSubmit: Action[AnyContent] = ActionWithMdc {
    SeeOther(routes.RegisterController.getCreateAccountHelpToSavePage().url)
  }

  /** Return `None` if user is ineligible */
  private def getEligibleUserInfo(session: Option[HTSSession])(
      implicit
      htsContext: HtsContextWithNINOAndUserDetails,
      hc:         HeaderCarrier): EitherT[Future, String, Either[Ineligible, EligibleWithUserInfo]] = session.flatMap(_.eligibilityCheckResult) match {

    case Some(eligibilityCheckResult) ⇒
      EitherT.fromEither[Future](
        eligibilityCheckResult.fold[Either[String, Either[Ineligible, EligibleWithUserInfo]]](
          r ⇒ Right(Left(r)), // IMPOSSIBLE - this means they are ineligible
          e ⇒ Right(Right(e))
        ))

    case None ⇒
      htsContext.userDetails.fold[EitherT[Future, String, Either[Ineligible, EligibleWithUserInfo]]](
        missingInfos ⇒ EitherT.fromEither[Future](Left(s"Missing user info: ${missingInfos.missingInfo}")),
        userInfo ⇒
          EitherT(
            helpToSaveService.checkEligibility().value.map {
              _.fold[Either[String, Either[Ineligible, EligibleWithUserInfo]]](
                Left(_), {
                  case e: Eligible          ⇒ Right(Right(EligibleWithUserInfo(e, userInfo)))
                  case i: Ineligible        ⇒ Right(Left(i))
                  case AlreadyHasAccount(_) ⇒ Left("User already has account")
                }
              )
            }
          ))
  }

  /** Return `None` if user is ineligible */
  private def updateSession(session: Option[HTSSession],
                            params:  EmailVerificationParams)(
      implicit
      htsContext: HtsContextWithNINOAndUserDetails,
      hc:         HeaderCarrier): EitherT[Future, String, Either[Ineligible, UserInfo]] = {

    getEligibleUserInfo(session).flatMap { e ⇒
      val result: Either[Ineligible, EitherT[Future, String, UserInfo]] = e.map { eligibleWithUserInfo ⇒
        if (eligibleWithUserInfo.userInfo.nino =!= params.nino) {
          EitherT.fromEither[Future](Left[String, UserInfo]("NINO in confirm details parameters did not match NINO from auth"))
        } else {
          val newInfo = eligibleWithUserInfo.userInfo.updateEmail(params.email)
          val newSession = HTSSession(Some(Right(eligibleWithUserInfo.copy(userInfo = newInfo))),
                                      Some(params.email), session.flatMap(_.pendingEmail))
          for {
            _ ← sessionCacheConnector.put(newSession)
            _ ← helpToSaveService.storeConfirmedEmail(params.email)
          } yield newInfo
        }
      }
      result.traverse[EitherTResult, Ineligible, UserInfo](identity)
    }
  }

}

