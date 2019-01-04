/*
 * Copyright 2019 HM Revenue & Customs
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
import com.google.inject.{Inject, Singleton}
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent, Request, Result}
import play.api.{Application, Configuration, Environment}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.auth.HelpToSaveAuth
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.connectors.EmailVerificationConnector
import uk.gov.hmrc.helptosavefrontend.controllers.EmailController.CannotCreateAccountReason
import uk.gov.hmrc.helptosavefrontend.controllers.EmailController.EligibleInfo.{EligibleWithEmail, EligibleWithNoEmail}
import uk.gov.hmrc.helptosavefrontend.forms._
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.models.HTSSession.EligibleWithUserInfo
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityCheckResultType.{AlreadyHasAccount, Eligible, Ineligible}
import uk.gov.hmrc.helptosavefrontend.models.userinfo.{NSIPayload, UserInfo}
import uk.gov.hmrc.helptosavefrontend.repo.SessionStore
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.Logging.LoggerOps
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, Email, EmailVerificationParams, NINOLogMessageTransformer, toFuture, Result ⇒ EitherTResult}
import uk.gov.hmrc.helptosavefrontend.views
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class EmailController @Inject() (val helpToSaveService:          HelpToSaveService,
                                 val sessionStore:               SessionStore,
                                 val emailVerificationConnector: EmailVerificationConnector,
                                 val authConnector:              AuthConnector,
                                 val metrics:                    Metrics,
                                 app:                            Application,
                                 val auditor:                    HTSAuditor)(implicit val crypto: Crypto,
                                                                             emailValidation:          EmailValidation,
                                                                             override val messagesApi: MessagesApi,
                                                                             val transformer:          NINOLogMessageTransformer,
                                                                             val frontendAppConfig:    FrontendAppConfig,
                                                                             val config:               Configuration,
                                                                             val env:                  Environment,
                                                                             ec:                       ExecutionContext)

  extends BaseController with HelpToSaveAuth with EnrolmentCheckBehaviour with SessionBehaviour with VerifyEmailBehaviour {

  private val eligibilityPage: String = routes.EligibilityCheckController.getIsEligible().url

  private def backLinkFromSession(session: HTSSession): String =
    if (session.changingDetails) {
      routes.RegisterController.getCreateAccountPage().url
    } else {
      eligibilityPage
    }

  def getSelectEmailPage: Action[AnyContent] =
    authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒

      def ifDigitalNewApplicant = { session: Option[HTSSession] ⇒
          withEligibleSession(
            {
              case (s, eligibleWithEmail) ⇒
                val emailFormWithData = if (s.changingDetails) {
                  SelectEmailForm.selectEmailForm.copy(data = Map("new-email" → ""))
                } else {
                  if (!s.hasSelectedEmail) {
                    SelectEmailForm.selectEmailForm
                  } else {
                    SelectEmailForm.selectEmailForm.copy(data =
                      s.pendingEmail.fold(Map("new-email" → ""))(e ⇒ Map("new-email" → e)))
                  }
                }
                Ok(views.html.email.select_email(eligibleWithEmail.email, emailFormWithData, Some(backLinkFromSession(s))))
            },
            { case _ ⇒ SeeOther(routes.EmailController.getGiveEmailPage().url) }
          )(session)
        }

        def ifDE = { _: Option[HTSSession] ⇒
          htsContext.userDetails.fold[Future[Result]](
            _ ⇒ SeeOther(routes.EmailController.getGiveEmailPage().url),
            userInfo ⇒ userInfo.email.fold[Future[Result]](
              SeeOther(routes.EmailController.getGiveEmailPage().url)
            )(
                email ⇒
                  emailValidation.validate(email).toEither match {
                    case Right(validEmail) ⇒
                      updateSessionAndReturnResult(
                        HTSSession(None, None, Some(validEmail)),
                        Ok(views.html.email.select_email(validEmail, SelectEmailForm.selectEmailForm)))

                    case Left(_) ⇒
                      updateSessionAndReturnResult(
                        HTSSession(None, None, None),
                        SeeOther(routes.EmailController.getGiveEmailPage().url)
                      )
                  }
              )
          )
        }

      checkSessionAndEnrolmentStatus(ifDigitalNewApplicant, ifDE)

    }(redirectOnLoginURL = routes.EmailController.getSelectEmailPage().url)

  def selectEmailSubmit(): Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒

    def handleForm(email:    String,
                   backLink: Option[String],
                   session:  HTSSession
      ): Future[Result] =
        SelectEmailForm.selectEmailForm.bindFromRequest().fold(
          withErrors ⇒ Ok(views.html.email.select_email(email, withErrors, backLink)),
          { form ⇒
            val (updatedSession, result) = form.newEmail.fold {
              session.copy(hasSelectedEmail = true) →
                SeeOther(routes.EmailController.emailConfirmed(crypto.encrypt(email)).url)
            } { newEmail ⇒
              session.copy(pendingEmail     = Some(newEmail), confirmedEmail = None, hasSelectedEmail = true) →
                SeeOther(routes.EmailController.confirmEmail().url)
            }

            if (updatedSession =!= session) {
              updateSessionAndReturnResult(updatedSession, result)
            } else {
              result
            }
          }
        )

      def ifDigitalNewApplicant = { maybeSession: Option[HTSSession] ⇒
        withEligibleSession({
          case (session, eligibleWithEmail) ⇒
            val backLink = backLinkFromSession(session)
            handleForm(eligibleWithEmail.email,
                       Some(backLink),
                       session
            )
        }, {
          case _ ⇒ SeeOther(routes.EmailController.getGiveEmailPage().url)
        })(maybeSession)
      }

      def ifDE = { maybeSession: Option[HTSSession] ⇒
        maybeSession.fold[Future[Result]](
          SeeOther(routes.EligibilityCheckController.getCheckEligibility().url)
        ) { session ⇒
            session.pendingEmail.fold[Future[Result]] {
              logger.warn("Could not find pending email for select email submit")
              internalServerError()
            } {
              email ⇒ handleForm(email, None, session)
            }
          }
      }

    checkSessionAndEnrolmentStatus(ifDigitalNewApplicant, ifDE)

  }(redirectOnLoginURL = routes.EmailController.selectEmailSubmit().url)

  def getGiveEmailPage: Action[AnyContent] =
    authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒

      def ifDigitalNewApplicant = { session: Option[HTSSession] ⇒
          withEligibleSession({
            case _ ⇒ SeeOther(routes.EmailController.getSelectEmailPage().url)
          }, {
            case (s, _) ⇒
              Ok(views.html.email.give_email(GiveEmailForm.giveEmailForm, Some(backLinkFromSession(s))))
          })(session)
        }

        def ifDE = { _: Option[HTSSession] ⇒
          htsContext.userDetails.toOption.flatMap(_.email).fold {
            updateSessionAndReturnResult(HTSSession(None, None, None),
                                         Ok(views.html.email.give_email(GiveEmailForm.giveEmailForm)))
          } {
            email ⇒
              emailValidation.validate(email).toEither match {
                case Right(validEmail) ⇒
                  updateSessionAndReturnResult(HTSSession(None, None, Some(validEmail)),
                                               SeeOther(routes.EmailController.getSelectEmailPage().url)
                  )

                case Left(_) ⇒
                  updateSessionAndReturnResult(HTSSession(None, None, None),
                                               Ok(views.html.email.give_email(GiveEmailForm.giveEmailForm))
                  )
              }
          }
        }

      checkSessionAndEnrolmentStatus(ifDigitalNewApplicant, ifDE)

    }(redirectOnLoginURL = routes.EmailController.getGiveEmailPage().url)

  def giveEmailSubmit(): Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒

    def handleForm(session: HTSSession): Future[Result] =
        GiveEmailForm.giveEmailForm.bindFromRequest().fold[Future[Result]](
          withErrors ⇒ Ok(views.html.email.give_email(withErrors, Some(backLinkFromSession(session)))),
          form ⇒ {
            val updatedSession = session.copy(confirmedEmail = None, pendingEmail = Some(form.email))
            if (session =!= updatedSession) {
              updateSessionAndReturnResult(updatedSession,
                                           SeeOther(routes.EmailController.confirmEmail().url))
            } else {
              SeeOther(routes.EmailController.confirmEmail().url)
            }
          })

      def ifDigitalNewApplicant(session: Option[HTSSession]) =
        withEligibleSession({
          case (s, _) ⇒ handleForm(s)
        }, {
          case (s, _) ⇒ handleForm(s)
        })(session)

      def ifDE = {
        htsSession: Option[HTSSession] ⇒
          htsSession.fold[Future[Result]](
            SeeOther(routes.EligibilityCheckController.getCheckEligibility().url)
          ) { _ ⇒
              GiveEmailForm.giveEmailForm.bindFromRequest().fold[Future[Result]](
                withErrors ⇒ Ok(views.html.email.give_email(withErrors)),
                form ⇒
                  updateSessionAndReturnResult(HTSSession(None, None, Some(form.email)),
                                               SeeOther(routes.EmailController.confirmEmail().url)
                  )
              )
            }
      }

    checkSessionAndEnrolmentStatus(ifDigitalNewApplicant, ifDE)

  }(redirectOnLoginURL = routes.EmailController.getGiveEmailPage().url)

  def emailConfirmed(email: String): Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒
    val nino = htsContext.nino
    val decryptedEmailEither = EitherT.fromEither[Future](decryptEmail(email))

      def doUpdate(hTSSession: HTSSession)(ifSuccess: ⇒ Future[Result]): Future[Result] = {
        val result: EitherT[Future, String, Result] = for {
          e ← decryptedEmailEither
          _ ← sessionStore.store(hTSSession.copy(confirmedEmail = Some(e)))
          _ ← helpToSaveService.storeConfirmedEmail(e)
          r ← EitherT.liftF(ifSuccess)
        } yield r

        result.fold(
          { e ⇒
            logger.warn(s"Could not write confirmed email: $e", nino)
            internalServerError()
          },
          identity
        )
      }

      def ifDigitalNewApplicant(session: Option[HTSSession]) =
        withEligibleSession(
          {
            case (session, _) ⇒
              doUpdate(session.copy(confirmedEmail = None, pendingEmail = None)) {
                //once email is confirmed and , if we were in the process of changing details then we should redirect user to check_details page
                if (session.changingDetails) {
                  SeeOther(routes.RegisterController.getCreateAccountPage().url)
                } else {
                  SeeOther(routes.BankAccountController.getBankDetailsPage().url)
                }
              }
          },
          { case _ ⇒ SeeOther(routes.EmailController.getGiveEmailPage().url) }
        )(session)

      def ifDE = {
        session: Option[HTSSession] ⇒
          withSession(session) { _ ⇒
            doUpdate(HTSSession(None, None, None)) {
              htsContext.userDetails.fold[Future[Result]](
                missingInfo ⇒ {
                  logger.warn(s"DE user missing infos, missing = ${missingInfo.missingInfo.mkString(",")}")
                  internalServerError()
                },
                userInfo ⇒ {
                  val result = for {
                    e ← decryptedEmailEither
                    _ ← helpToSaveService.updateEmail(NSIPayload(userInfo.copy(email = Some(e)), e, frontendAppConfig.version, frontendAppConfig.systemId))
                    r ← EitherT.liftF(toFuture(SeeOther(frontendAppConfig.nsiManageAccountUrl)))
                    _ ← {
                      val auditEvent = EmailChanged(htsContext.nino, "", e, duringRegistrationJourney = false, routes.EmailController.emailConfirmed(email).url)
                      auditor.sendEvent(auditEvent, htsContext.nino)
                      EitherT.pure[Future, String](())
                    }
                  } yield r

                  result.fold[Result](
                    errors ⇒ {
                      logger.warn(s"error during update email, error = $errors")
                      internalServerError()
                    },
                    identity
                  )
                }
              )
            }
          }
      }

    checkSessionAndEnrolmentStatus(ifDigitalNewApplicant, ifDE)

  }(redirectOnLoginURL = routes.EmailController.emailConfirmed(email).url)

  def emailConfirmedCallback(emailVerificationParams: String): Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒
    val result: EitherT[Future, String, Result] =
      for {
        s ← helpToSaveService.getUserEnrolmentStatus()
        r ← handleCallback(s, emailVerificationParams, routes.EmailController.emailConfirmedCallback(emailVerificationParams).url)
      } yield r

    result.leftMap { e ⇒
      logger.warn(e)
      internalServerError()
    }.merge
  }(redirectOnLoginURL = routes.EmailController.emailConfirmedCallback(emailVerificationParams).url)

  private def handleCallback(status: EnrolmentStatus, emailVerificationParams: String, path: String)(
      implicit
      hc: HeaderCarrier, h: HtsContextWithNINOAndUserDetails, request: Request[AnyContent]): EitherT[Future, String, Result] =
    status.fold[EitherT[Future, String, Result]](
      handleCallBackForDigital(emailVerificationParams, path),
      _ ⇒ handleCallBackForDE(emailVerificationParams, path)
    )

  private def handleCallBackForDigital(emailVerificationParams: String,
                                       path:                    String)(implicit htsContext: HtsContextWithNINOAndUserDetails,
                                                                        hc:      HeaderCarrier,
                                                                        request: Request[AnyContent]): EitherT[Future, String, Result] = {
    withEmailVerificationParameters(
      emailVerificationParams,
      { params ⇒
          def handleResult(result: Either[CannotCreateAccountReason, (Option[String], UserInfo)]): Result =
            result.fold({
              createAccountVoid ⇒
                createAccountVoid.result.fold(
                  _ ⇒ SeeOther(routes.EmailController.getLinkExpiredPage().url),
                  _ ⇒ SeeOther(routes.EligibilityCheckController.getIsNotEligible().url)
                )
            },
              {
                case (oldEmail, updatedUserInfo) ⇒
                  val auditEvent = EmailChanged(updatedUserInfo.nino, oldEmail.getOrElse(""), params.email, duringRegistrationJourney = true, path)
                  auditor.sendEvent(auditEvent, updatedUserInfo.nino)
                  SeeOther(routes.EmailController.getEmailConfirmed().url)
              })

        for {
          session ← sessionStore.get
          updateSessionResult ← updateSession(session, params)
          result ← EitherT.pure[Future, String](handleResult(updateSessionResult))
        } yield result

      }, EitherT.pure[Future, String](SeeOther(routes.EmailController.confirmEmailErrorTryLater().url))
    )(path)
  }

  private def handleCallBackForDE(emailVerificationParams: String,
                                  path:                    String)(implicit htsContext: HtsContextWithNINOAndUserDetails,
                                                                   hc:      HeaderCarrier,
                                                                   request: Request[AnyContent]): EitherT[Future, String, Result] = {
    htsContext.userDetails.fold[EitherT[Future, String, Result]](
      missingInfo ⇒ {
        logger.warn(s"DE user missing infos, missing = ${missingInfo.missingInfo.mkString(",")}")
        EitherT.pure[Future, String](internalServerError())
      },
      userInfo ⇒ {
        withEmailVerificationParameters(
          emailVerificationParams,
          params ⇒ {
            for {
              _ ← helpToSaveService.updateEmail(NSIPayload(userInfo.copy(email = Some(params.email)), params.email, frontendAppConfig.version, frontendAppConfig.systemId))
              _ ← helpToSaveService.storeConfirmedEmail(params.email)
              r ← EitherT.liftF(updateSessionAndReturnResult(
                HTSSession(None, Some(params.email), None),
                SeeOther(routes.EmailController.getEmailConfirmed().url))
              )
              _ ← {
                val auditEvent = EmailChanged(params.nino, "", params.email, duringRegistrationJourney = false, path)
                auditor.sendEvent(auditEvent, params.nino)
                EitherT.pure[Future, String](())
              }
            } yield r
          },
          {
            logger.warn("invalid emailVerificationParams during email verification")
            EitherT.pure[Future, String](SeeOther(routes.EmailController.confirmEmailErrorTryLater().url))
          }
        )(path)
      }
    )
  }

  private def updateSessionAndReturnResult(session:      HTSSession,
                                           ifSuccessful: ⇒ Result)(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] =
    sessionStore.store(session).fold[Result](
      e ⇒ {
        logger.warn(s"error updating the session, error = $e")
        internalServerError()
      },
      _ ⇒ ifSuccessful
    )

  /** Return `None` if user is ineligible */
  private def updateSession(session: Option[HTSSession],
                            params:  EmailVerificationParams)(
      implicit
      htsContext: HtsContextWithNINOAndUserDetails,
      hc:         HeaderCarrier): EitherT[Future, String, Either[CannotCreateAccountReason, (Option[String], UserInfo)]] = {

    getEligibleUserInfo(session).flatMap { e ⇒
      val result: Either[CannotCreateAccountReason, EitherT[Future, String, (Option[String], UserInfo)]] = e.map { eligibleWithUserInfo ⇒
        if (eligibleWithUserInfo.userInfo.nino =!= params.nino) {
          EitherT.fromEither[Future](Left("NINO in confirm details parameters did not match NINO from auth"))
        } else {
          val newInfo = eligibleWithUserInfo.userInfo.updateEmail(params.email)
          val newSession = HTSSession(Some(Right(eligibleWithUserInfo.copy(userInfo = newInfo))),
                                      Some(params.email), session.flatMap(_.pendingEmail), None, None,
                                      session.flatMap(_.bankDetails), session.exists(_.changingDetails))
          for {
            _ ← sessionStore.store(newSession)
            _ ← helpToSaveService.storeConfirmedEmail(params.email)
          } yield eligibleWithUserInfo.userInfo.email → newInfo
        }
      }
      result.traverse[EitherTResult, CannotCreateAccountReason, (Option[String], UserInfo)](identity)
    }
  }

  /** Return `None` if user is ineligible */
  private def getEligibleUserInfo(session: Option[HTSSession])(
      implicit
      htsContext: HtsContextWithNINOAndUserDetails,
      hc:         HeaderCarrier): EitherT[Future, String, Either[CannotCreateAccountReason, EligibleWithUserInfo]] = session.flatMap(_.eligibilityCheckResult) match {

    case Some(eligibilityCheckResult) ⇒
      EitherT.fromEither[Future](
        eligibilityCheckResult.fold[Either[String, Either[CannotCreateAccountReason, EligibleWithUserInfo]]](
          r ⇒ Right(Left(CannotCreateAccountReason(r))), // IMPOSSIBLE - this means they are ineligible
          e ⇒ Right(Right(e))
        ))

    case None ⇒
      htsContext.userDetails.fold[EitherT[Future, String, Either[CannotCreateAccountReason, EligibleWithUserInfo]]](
        missingInfos ⇒ EitherT.fromEither[Future](Left(s"Missing user info: ${missingInfos.missingInfo}")),
        userInfo ⇒
          EitherT(
            helpToSaveService.checkEligibility().value.map {
              _.fold[Either[String, Either[CannotCreateAccountReason, EligibleWithUserInfo]]](
                Left(_), {
                  case e: Eligible          ⇒ Right(Right(EligibleWithUserInfo(e, userInfo)))
                  case i: Ineligible        ⇒ Right(Left(CannotCreateAccountReason(i)))
                  case a: AlreadyHasAccount ⇒ Right(Left(CannotCreateAccountReason(a)))
                }
              )
            }
          ))
  }

  def confirmEmail: Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒

    def sendVerificationRequest(pendingEmail: String, userInfo: UserInfo) =
        sendEmailVerificationRequest(
          pendingEmail,
          userInfo.forename,
          Ok(views.html.email.check_your_email(pendingEmail, userInfo.email)),
          params ⇒ routes.EmailController.emailConfirmedCallback(params.encode()).url,
          _ ⇒ SeeOther(userInfo.email.fold(
            routes.EmailController.confirmEmailErrorTryLater().url)(
              _ ⇒ routes.EmailController.confirmEmailError().url)),
          isNewApplicant = true)

      def ifDigitalNewApplicant(session: Option[HTSSession]) =
        withEligibleSession({
          case (s, eligibleWithEmail) ⇒
            s.pendingEmail.fold[Future[Result]](
              internalServerError()
            ) { pendingEmail ⇒
                sendVerificationRequest(pendingEmail, eligibleWithEmail.userInfo)
              }
        })(session)

      def ifDE = {
        session: Option[HTSSession] ⇒
          {
            session.flatMap(_.pendingEmail).fold[Future[Result]](
              internalServerError()
            ) { pendingEmail ⇒
                htsContext.userDetails.fold[Future[Result]](
                  missingInfo ⇒ {
                    logger.warn(s"user missing infos, missing = ${missingInfo.missingInfo.mkString(",")}")
                    internalServerError()
                  },
                  userInfo ⇒ {
                    sendVerificationRequest(pendingEmail, userInfo)
                  }
                )
              }
          }
      }

    checkSessionAndEnrolmentStatus(ifDigitalNewApplicant, ifDE)
  }(redirectOnLoginURL = routes.EmailController.confirmEmail().url)

  def confirmEmailError: Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒

    def ifDigitalNewApplicant(session: Option[HTSSession]) =
        withEligibleSession {
          case (_, eligible) ⇒
            eligible.userInfo.email.fold(
              SeeOther(routes.EmailController.confirmEmailErrorTryLater().url)
            )(email ⇒ Ok(views.html.email.cannot_change_email(email, EmailVerificationErrorContinueForm.continueForm, duringRegistrationJourney = true)))
        }(session)

      def ifDE = {
        _: Option[HTSSession] ⇒
          {
            htsContext.userDetails.toOption.flatMap(_.email).fold[Future[Result]](
              SeeOther(routes.EmailController.confirmEmailErrorTryLater().url)
            )(email ⇒
                Ok(views.html.email.cannot_change_email(email, EmailVerificationErrorContinueForm.continueForm, duringRegistrationJourney = false)))
          }
      }

    checkSessionAndEnrolmentStatus(ifDigitalNewApplicant, ifDE)

  }(redirectOnLoginURL = routes.EmailController.confirmEmailError().url)

  def confirmEmailErrorTryLater: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    checkSessionAndEnrolmentStatus(
      { _ ⇒ Ok(views.html.email.cannot_change_email_try_later(returningUser = false, None)) },
      { _ ⇒ Ok(views.html.email.cannot_change_email_try_later(returningUser = true, None)) },
      { (_, email) ⇒ Ok(views.html.email.cannot_change_email_try_later(returningUser = true, Some(email))) }
    )

  }(redirectOnLoginURL = routes.EmailController.confirmEmailErrorTryLater().url)

  def confirmEmailErrorSubmit: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒

    def handleForm(email: Option[String], duringRegistrationJourney: Boolean) =
        email.fold[Future[Result]](
          SeeOther(routes.EmailController.confirmEmailErrorTryLater().url)
        )(e ⇒
            EmailVerificationErrorContinueForm.continueForm.bindFromRequest().fold(
              form ⇒ Ok(views.html.email.cannot_change_email(e, form, duringRegistrationJourney)),
              { continue ⇒
                if (continue.value) {
                  SeeOther(routes.EmailController.emailConfirmed(crypto.encrypt(e)).url)
                } else {
                  SeeOther(routes.IntroductionController.getAboutHelpToSave().url)
                }
              }
            )
          )

      def ifDigitalNewApplicant(session: Option[HTSSession]) =
        withEligibleSession {
          case (_, eligible) ⇒
            handleForm(eligible.userInfo.email, true)
        }(session)

      def ifDE =
        (session: Option[HTSSession]) ⇒ handleForm(session.flatMap(_.confirmedEmail), false)

    checkSessionAndEnrolmentStatus(ifDigitalNewApplicant, ifDE)

  }(redirectOnLoginURL = routes.EmailController.confirmEmailError().url)

  val getLinkExpiredPage: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    Ok(views.html.link_expired())
  }(redirectOnLoginURL = routes.EmailController.getLinkExpiredPage().url)

  def getEmailConfirmed: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒

    def ifDigitalNewApplicant(session: Option[HTSSession]) =
        withEligibleSession {
          case (s, eligible) ⇒
            s.confirmedEmail.fold[Future[Result]] {
              logger.warn("Could not find confirmed email", htsContext.nino)
              val url = eligible.userInfo.email.fold(
                routes.EmailController.confirmEmailErrorTryLater().url)(
                  _ ⇒ routes.EmailController.confirmEmailError().url
                )
              toFuture(SeeOther(url))
            }(_ ⇒ Ok(views.html.email.email_updated()))
        }(session)

      def ifDE = SeeOther(routes.EmailController.getGiveEmailPage().url)

      def ifDigitalAccountHolder = {
        (session: Option[HTSSession], _: Email) ⇒
          withSession(session) {
            _.confirmedEmail.fold[Future[Result]] {
              logger.warn("Could not find confirmed email in the session for DE user", htsContext.nino)
              internalServerError()
            }(_ ⇒ Ok(views.html.email.email_updated())
            )
          }
      }

    checkSessionAndEnrolmentStatus(ifDigitalNewApplicant, _ ⇒ ifDE, ifDigitalAccountHolder)

  }(redirectOnLoginURL = routes.EmailController.getEmailConfirmed().url)

  def getEmailUpdated: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒

    def handle(email: Option[String]) =
        email.fold[Future[Result]](
          SeeOther(routes.EmailController.getGiveEmailPage().url))(
            _ ⇒ Ok(views.html.email.email_updated()))

      def ifDigitalNewApplicant(session: Option[HTSSession]) =
        withEligibleSession {
          case (_, eligible) ⇒
            handle(eligible.userInfo.email)
        }(session)

      def ifDE = SeeOther(routes.EmailController.getGiveEmailPage().url)

      def ifDigitalAccountHolder = {
        (session: Option[HTSSession], _: Email) ⇒
          withSession(session) {
            s ⇒ handle(s.confirmedEmail)
          }
      }

    checkSessionAndEnrolmentStatus(ifDigitalNewApplicant, _ ⇒ ifDE, ifDigitalAccountHolder)

  }(redirectOnLoginURL = routes.EmailController.getEmailUpdated().url)

  def emailUpdatedSubmit: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    checkSessionAndEnrolmentStatus(
      mayBeSession ⇒ {
        mayBeSession.fold(SeeOther(routes.EligibilityCheckController.getCheckEligibility().url))(
          session ⇒
            if (session.changingDetails) {
              SeeOther(routes.RegisterController.getCreateAccountPage().url)
            } else {
              SeeOther(routes.BankAccountController.getBankDetailsPage().url)
            }
        )
      },
      _ ⇒ SeeOther(routes.EmailController.getGiveEmailPage().url)
    )
  }(redirectOnLoginURL = routes.EmailController.emailUpdatedSubmit().url)

  private def withSession(session: Option[HTSSession])(f: HTSSession ⇒ Future[Result]): Future[Result] =
    session.fold[Future[Result]](SeeOther(routes.EligibilityCheckController.getCheckEligibility().url))(f)

  private def withEligibleSession(
      ifEligible: (HTSSession, EligibleWithUserInfo) ⇒ Future[Result]
  )(session: Option[HTSSession]): Future[Result] =
    session.fold[Future[Result]](SeeOther(routes.EligibilityCheckController.getCheckEligibility().url))(s ⇒
      s.eligibilityCheckResult.fold[Future[Result]](
        SeeOther(routes.EligibilityCheckController.getCheckEligibility().url)
      ) {
          _.fold(
            _ ⇒ SeeOther(routes.EligibilityCheckController.getIsNotEligible().url),
            eligible ⇒ ifEligible(s, eligible)
          )
        }
    )

  private def withEligibleSession(
      ifEligibleWithEmail:   (HTSSession, EligibleWithEmail) ⇒ Future[Result],
      ifEligibleWithNoEmail: (HTSSession, EligibleWithNoEmail) ⇒ Future[Result]
  )(session: Option[HTSSession]): Future[Result] =
    session.fold[Future[Result]](SeeOther(routes.EligibilityCheckController.getCheckEligibility().url))(s ⇒
      s.eligibilityCheckResult.fold[Future[Result]](
        SeeOther(routes.EligibilityCheckController.getCheckEligibility().url)
      ) {
          _.fold(
            _ ⇒ SeeOther(routes.EligibilityCheckController.getIsNotEligible().url),
            eligible ⇒
              eligible.userInfo.email.fold(
                ifEligibleWithNoEmail(s, EligibleWithNoEmail(eligible.userInfo, eligible.eligible, s.bankDetails))
              )(email ⇒
                  emailValidation.validate(email).toEither match {
                    case Left(e) ⇒
                      logger.warn(s"GG email was invalid: $e")
                      ifEligibleWithNoEmail(s, EligibleWithNoEmail(eligible.userInfo, eligible.eligible, s.bankDetails))

                    case Right(e) ⇒
                      ifEligibleWithEmail(s, EligibleWithEmail(eligible.userInfo, e, s.confirmedEmail, eligible.eligible, s.bankDetails))

                  }
                )
          )
        }
    )

  private def checkSessionAndEnrolmentStatus(ifDuringRegistrationJourney: Option[HTSSession] ⇒ Future[Result],
                                             ifDE:                        Option[HTSSession] ⇒ Future[Result],
                                             ifDigitalAccountHolder: (Option[HTSSession], Email) ⇒ Future[Result] = {
                                               case _⇒ SeeOther(frontendAppConfig.nsiManageAccountUrl)
                                             }
  )(implicit hc: HeaderCarrier,
    request: Request[_]): Future[Result] = {
    val result = for {
      session ← sessionStore.get
      enrolmentStatus ← helpToSaveService.getUserEnrolmentStatus()
    } yield session → enrolmentStatus

    result
      .leftSemiflatMap { e ⇒
        logger.warn(s"Could not get session or enrolment status: $e")
        internalServerError
      }.semiflatMap {
        case (session, enrolmentStatus) ⇒
          enrolmentStatus.fold(
            ifDuringRegistrationJourney(session),
            { _ ⇒
              helpToSaveService.getConfirmedEmail
                .leftSemiflatMap { e ⇒
                  logger.warn(s"Could not get confirmed email: $e")
                  internalServerError()
                }
                .semiflatMap {
                  // Digital account holder
                  case Some(email) ⇒ ifDigitalAccountHolder(session, email)
                  // DE account holder
                  case None        ⇒ ifDE(session)
                }.merge

            })
      }.merge
  }

  private def decryptEmail(encryptedEmail: String): Either[String, String] =
    crypto.decrypt(encryptedEmail) match {
      case Success(value) ⇒ Right(value)
      case Failure(e)     ⇒ Left(s"Could not decode email: ${e.getMessage}")
    }
}

object EmailController {

  sealed trait EligibleInfo

  object EligibleInfo {

    case class EligibleWithEmail(userInfo: UserInfo, email: Email, confirmedEmail: Option[Email], eligible: Eligible, bankDetails: Option[BankDetails]) extends EligibleInfo

    case class EligibleWithNoEmail(userInfo: UserInfo, eligible: Eligible, bankDetails: Option[BankDetails]) extends EligibleInfo

  }

  private case class CannotCreateAccountReason(result: Either[AlreadyHasAccount, Ineligible])

  private object CannotCreateAccountReason {
    def apply(ineligible: Ineligible): CannotCreateAccountReason = CannotCreateAccountReason(Right(ineligible))

    def apply(alreadyHasAccount: AlreadyHasAccount): CannotCreateAccountReason = CannotCreateAccountReason(Left(alreadyHasAccount))
  }

}
