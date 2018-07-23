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
import com.google.inject.{Inject, Singleton}
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent, Request, Result}
import play.api.{Application, Configuration, Environment}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.auth.HelpToSaveAuth
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.connectors.{EmailVerificationConnector, SessionCacheConnector}
import uk.gov.hmrc.helptosavefrontend.controllers.EmailController.CannotCreateAccountReason
import uk.gov.hmrc.helptosavefrontend.controllers.EmailController.EligibleInfo.{EligibleWithEmail, EligibleWithNoEmail}
import uk.gov.hmrc.helptosavefrontend.forms.{EmailValidation, EmailVerificationErrorContinueForm, GiveEmailForm, SelectEmailForm}
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.models.EnrolmentStatus.{Enrolled, NotEnrolled}
import uk.gov.hmrc.helptosavefrontend.models.HTSSession.EligibleWithUserInfo
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityCheckResult.{AlreadyHasAccount, Eligible, Ineligible}
import uk.gov.hmrc.helptosavefrontend.models.userinfo.{NSIUserInfo, UserInfo}
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.Logging.LoggerOps
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, Email, EmailVerificationParams, NINOLogMessageTransformer, toFuture, Result ⇒ EitherTResult}
import uk.gov.hmrc.helptosavefrontend.views
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future
import scala.util.{Failure, Success}

@Singleton
class EmailController @Inject() (val helpToSaveService:          HelpToSaveService,
                                 val sessionCacheConnector:      SessionCacheConnector,
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
                                                                             val env:                  Environment)

  extends BaseController with HelpToSaveAuth with EnrolmentCheckBehaviour with SessionBehaviour with VerifyEmailBehaviour {

  def getSelectEmailPage: Action[AnyContent] =
    authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒

      def ifDigital: Future[Result] =
          checkIfDoneEligibilityChecks { eligibleWithEmail ⇒
            Ok(views.html.email.select_email(eligibleWithEmail.email, SelectEmailForm.selectEmailForm))
          } { _ ⇒
            SeeOther(routes.EmailController.getGiveEmailPage().url)
          }

        def ifDE: Future[Result] =
          htsContext.userDetails.fold[Future[Result]](
            _ ⇒ SeeOther(routes.EmailController.getGiveEmailPage().url),
            userInfo ⇒ userInfo.email.fold[Future[Result]](
              SeeOther(routes.EmailController.getGiveEmailPage().url)
            )(
                email ⇒
                  emailValidation.validate(email).toEither match {
                    case Right(validEmail) ⇒
                      updateSessionAndReturnResult(HTSSession(None, None, Some(validEmail), isDigital = false),
                                                   Ok(views.html.email.select_email(validEmail, SelectEmailForm.selectEmailForm)))

                    case Left(_) ⇒
                      updateSessionAndReturnResult(HTSSession(None, None, None, false),
                                                   SeeOther(routes.EmailController.getGiveEmailPage().url)
                      )
                  }
              )
          )

      checkIfDigitalAlready(ifDigital, ifDE)

    }(redirectOnLoginURL = routes.EmailController.getSelectEmailPage().url)

  def selectEmailSubmit(): Action[AnyContent] =
    authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒

      def handleForm(email: String, eligibilityCheckResult: Option[Either[Ineligible, EligibleWithUserInfo]], isDigital: Boolean = true): Future[Result] =
          SelectEmailForm.selectEmailForm.bindFromRequest().fold(
            withErrors ⇒ Ok(views.html.email.select_email(email, withErrors)),
            _.newEmail.fold[Future[Result]](

              SeeOther(routes.EmailController.confirmEmail(crypto.encrypt(email)).url))(
                newEmail ⇒ {
                  updateSessionAndReturnResult(HTSSession(eligibilityCheckResult, None, Some(newEmail), isDigital),
                                               SeeOther(routes.EmailController.verifyEmail().url)
                  )
                }
              )
          )

        def ifDigital: Future[Result] =
          checkIfDoneEligibilityChecks { eligibleWithEmail ⇒
            handleForm(eligibleWithEmail.email, Some(Right(EligibleWithUserInfo(eligibleWithEmail.eligible, eligibleWithEmail.userInfo))))
          } { _ ⇒
            SeeOther(routes.EmailController.getGiveEmailPage().url)
          }

        def ifDE = { htsSession: HTSSession ⇒
          htsSession.pendingEmail.fold[Future[Result]](
            {
              logger.warn("expecting a stored pending email in session, but not found")
              internalServerError()
            }
          )(email ⇒ {
              handleForm(email, None, false)
            }
            )
        }

      checkSessionExists(ifDigital, ifDE)

    }(redirectOnLoginURL = routes.EmailController.selectEmailSubmit().url)

  def getGiveEmailPage: Action[AnyContent] =
    authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒

      def ifDigital =
          checkIfDoneEligibilityChecks { eligibleWithEmail ⇒
            SeeOther(routes.EmailController.getSelectEmailPage().url)
          } { _ ⇒
            Ok(views.html.email.give_email(GiveEmailForm.giveEmailForm))
          }

        def ifDE =
          htsContext.userDetails.fold(
            _ ⇒ {
              updateSessionAndReturnResult(HTSSession(None, None, None, false),
                                           Ok(views.html.email.give_email(GiveEmailForm.giveEmailForm))
              )
            },
            userInfo ⇒ userInfo.email.fold[Future[Result]]({
              updateSessionAndReturnResult(HTSSession(None, None, None, false),
                                           Ok(views.html.email.give_email(GiveEmailForm.giveEmailForm))
              )
            }
            )(
              email ⇒
                emailValidation.validate(email).toEither match {
                  case Right(validEmail) ⇒
                    updateSessionAndReturnResult(HTSSession(None, None, Some(validEmail), false),
                                                 SeeOther(routes.EmailController.getSelectEmailPage().url)
                    )

                  case Left(_) ⇒
                    updateSessionAndReturnResult(HTSSession(None, None, None, false),
                                                 Ok(views.html.email.give_email(GiveEmailForm.giveEmailForm))
                    )
                }
            )
          )

      checkIfDigitalAlready(ifDigital, ifDE)

    }(redirectOnLoginURL = routes.EmailController.getGiveEmailPage().url)

  def giveEmailSubmit(): Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒

    def handleForm(eligible: Eligible, userInfo: UserInfo): Future[Result] =
        GiveEmailForm.giveEmailForm.bindFromRequest().fold[Future[Result]](
          withErrors ⇒ Ok(views.html.email.give_email(withErrors)),
          form ⇒
            updateSessionAndReturnResult(HTSSession(Some(Right(EligibleWithUserInfo(eligible, userInfo))), None, Some(form.email)),
                                         SeeOther(routes.EmailController.verifyEmail().url)
            ))

      def ifDigital =
        checkIfDoneEligibilityChecks { eligibleWithEmail ⇒
          handleForm(eligibleWithEmail.eligible, eligibleWithEmail.userInfo)
        } { eligibleWithNoEmail ⇒
          handleForm(eligibleWithNoEmail.eligible, eligibleWithNoEmail.userInfo)
        }

      def ifDE = {
        session: HTSSession ⇒
          {
            GiveEmailForm.giveEmailForm.bindFromRequest().fold[Future[Result]](
              withErrors ⇒ Ok(views.html.email.give_email(withErrors)),
              form ⇒
                updateSessionAndReturnResult(HTSSession(None, None, Some(form.email), false),
                                             SeeOther(routes.EmailController.verifyEmail().url)
                )
            )
          }
      }

    checkSessionExists(ifDigital, ifDE)

  }(redirectOnLoginURL = routes.EmailController.getGiveEmailPage().url)

  private def checkIfDigitalAlready(ifDigital: ⇒ Future[Result],
                                    ifDE:      ⇒ Future[Result])(implicit htsContext: HtsContextWithNINO,
                                                                 hc:      HeaderCarrier,
                                                                 request: Request[_]): Future[Result] = {
    val r = for {
      enrolmentStatus ← helpToSaveService.getUserEnrolmentStatus()
      storedEmail ← helpToSaveService.getConfirmedEmail()
    } yield (enrolmentStatus, storedEmail)

    r.leftSemiflatMap { error ⇒
      logger.warn(s"Error while trying to check user enrolment status: $error", htsContext.nino)
      internalServerError()
    }.semiflatMap {
      case (Enrolled(_), Some(_)) ⇒
        //Digital users with stored email should be in the /account-home flow
        logger.warn(s"not expecting email requests from Digital users with stored email in session", htsContext.nino)
        SeeOther(frontendAppConfig.nsiManageAccountUrl)

      case (Enrolled(_), None) ⇒ //DE user as no email found
        ifDE

      case (NotEnrolled, _) ⇒ //Digital user Registration
        ifDigital
    }.merge
  }

  def confirmEmail(email: String): Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒
    val nino = htsContext.nino
    val decryptedEmailEither = EitherT.fromEither[Future](decryptEmail(email))

      def doUpdate(hTSSession: HTSSession)(ifSuccess: ⇒ Future[Result]): Future[Result] = {
        val result: EitherT[Future, String, Result] = for {
          e ← decryptedEmailEither
          _ ← sessionCacheConnector.put(hTSSession.copy(confirmedEmail = Some(e)))
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

      def ifDigital =
        checkIfAlreadyEnrolled { () ⇒
          checkIfDoneEligibilityChecks { eligibleWithEmail ⇒
            doUpdate(HTSSession(Some(Right(EligibleWithUserInfo(eligibleWithEmail.eligible, eligibleWithEmail.userInfo))), None, None))(
              toFuture(SeeOther(routes.RegisterController.getCreateAccountPage().url))
            )
          } { _ ⇒
            SeeOther(routes.EmailController.getGiveEmailPage().url)
          }
        }

      def ifDE = {
        _: HTSSession ⇒
          {
            doUpdate(HTSSession(None, None, None, false)) {
              htsContext.userDetails.fold[Future[Result]](
                missingInfo ⇒ {
                  logger.warn(s"DE user missing infos, missing = ${missingInfo.missingInfo.mkString(",")}")
                  internalServerError()
                },
                userInfo ⇒ {
                  val result = for {
                    e ← decryptedEmailEither
                    _ ← helpToSaveService.updateEmail(NSIUserInfo(userInfo.copy(email = Some(e)), e))
                    r ← EitherT.liftF(toFuture(SeeOther(frontendAppConfig.nsiManageAccountUrl)))
                    _ ← {
                      val auditEvent = EmailChanged(htsContext.nino, "", e, duringRegistrationJourney = false)
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

    checkSessionExists(ifDigital, ifDE)

  }(redirectOnLoginURL = routes.EmailController.confirmEmail(email).url)

  /**
   * Checks the HTSSession data from keystore - if the is no session the user has not done the eligibility
   * checks yet this session and they are redirected to the 'apply now' page. If the session data indicates
   * that they are not eligible show the user the 'you are not eligible page'. Otherwise, perform the
   * given action if the the session data indicates that they are eligible
   */
  private def checkIfDoneEligibilityChecks(
      ifEligibleWithEmail: EligibleWithEmail ⇒ Future[Result])(
      ifEligibleWithoutEmail: EligibleWithNoEmail ⇒ Future[Result])(
      implicit
      htsContext: HtsContextWithNINO, hc: HeaderCarrier, request: Request[_]): Future[Result] =
    checkSession {
      // no session data => user has not gone through the journey this session => take them to eligibility checks
      SeeOther(routes.EligibilityCheckController.getCheckEligibility().url)
    } {
      session ⇒
        session.eligibilityCheckResult.fold[Future[Result]](
          SeeOther(routes.EligibilityCheckController.getCheckEligibility().url)
        )(_.fold(
            // user has gone through journey already this sessions and were found to be ineligible
            _ ⇒ SeeOther(routes.EligibilityCheckController.getIsNotEligible().url),
            userInfo ⇒
              // user has gone through journey already this sessions and were found to be eligible
              userInfo.userInfo.email.fold(ifEligibleWithoutEmail(EligibleWithNoEmail(userInfo.userInfo, userInfo.eligible)))(email ⇒
                emailValidation.validate(email).toEither match {
                  case Right(validEmail) ⇒ ifEligibleWithEmail(EligibleWithEmail(userInfo.userInfo, validEmail, session.confirmedEmail, userInfo.eligible))
                  case Left(_)           ⇒ ifEligibleWithoutEmail(EligibleWithNoEmail(userInfo.userInfo, userInfo.eligible))
                }
              )
          ))
    }

  private def decryptEmail(encryptedEmail: String): Either[String, String] =
    crypto.decrypt(encryptedEmail) match {
      case Success(value) ⇒ Right(value)
      case Failure(e)     ⇒ Left(s"Could not decode email: ${e.getMessage}")
    }

  def emailVerifiedCallback(emailVerificationParams: String): Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒
    val result: EitherT[Future, String, Result] =
      for {
        s ← helpToSaveService.getUserEnrolmentStatus()
        r ← handleCallback(s, emailVerificationParams)
      } yield r

    result.leftMap { e ⇒
      logger.warn(e)
      internalServerError()
    }.merge
  }(redirectOnLoginURL = routes.EmailController.emailVerifiedCallback(emailVerificationParams).url)

  private def handleCallback(status: EnrolmentStatus, emailVerificationParams: String)(
      implicit
      hc: HeaderCarrier, h: HtsContextWithNINOAndUserDetails, request: Request[AnyContent]): EitherT[Future, String, Result] =
    status.fold[EitherT[Future, String, Result]](
      handleCallBackForDigital(emailVerificationParams),
      _ ⇒ handleCallBackForDE(emailVerificationParams)
    )

  private def handleCallBackForDigital(emailVerificationParams: String)(implicit htsContext: HtsContextWithNINOAndUserDetails,
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
                  val auditEvent = EmailChanged(updatedUserInfo.nino, oldEmail.getOrElse(""), params.email, duringRegistrationJourney = true)
                  auditor.sendEvent(auditEvent, updatedUserInfo.nino)
                  SeeOther(routes.EmailController.getEmailVerified().url)
              })

        for {
          session ← sessionCacheConnector.get
          updateSessionResult ← updateSession(session, params)
          result ← EitherT.pure[Future, String](handleResult(updateSessionResult))
        } yield result

      }, EitherT.pure[Future, String](SeeOther(routes.EmailController.verifyEmailErrorTryLater().url))
    )
  }

  private def handleCallBackForDE(emailVerificationParams: String)(implicit htsContext: HtsContextWithNINOAndUserDetails,
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
              _ ← helpToSaveService.updateEmail(NSIUserInfo(userInfo.copy(email = Some(params.email)), params.email))
              _ ← helpToSaveService.storeConfirmedEmail(params.email)
              r ← EitherT.liftF(updateSessionAndReturnResult(
                HTSSession(None, Some(params.email), None, false),
                SeeOther(routes.EmailController.getEmailVerified().url))
              )
              _ ← {
                val auditEvent = EmailChanged(params.nino, "", params.email, duringRegistrationJourney = false)
                auditor.sendEvent(auditEvent, params.nino)
                EitherT.pure[Future, String](())
              }
            } yield r
          },
          {
            logger.warn("invalid emailVerificationParams during email verification")
            EitherT.pure[Future, String](SeeOther(routes.EmailController.verifyEmailErrorTryLater().url))
          }
        )
      }
    )
  }

  private def updateSessionAndReturnResult(session:      HTSSession,
                                           ifSuccessful: ⇒ Result)(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] =
    sessionCacheConnector.put(session).fold[Result](
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
                                      Some(params.email), session.flatMap(_.pendingEmail))
          for {
            _ ← sessionCacheConnector.put(newSession)
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

  def verifyEmail: Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒

    def sendVerificationRequest(pendingEmail: String, userInfo: UserInfo) =
        sendEmailVerificationRequest(
          pendingEmail,
          userInfo.forename,
          Ok(views.html.email.check_your_email(pendingEmail, userInfo.email)),
          params ⇒ routes.EmailController.emailVerifiedCallback(params.encode()).url,
          _ ⇒ SeeOther(userInfo.email.fold(
            routes.EmailController.verifyEmailErrorTryLater().url)(
              _ ⇒ routes.EmailController.verifyEmailError().url)),
          isNewApplicant = true)

      def ifDigital =
        checkEnrolledAndSession {
          case (userInfo, maybePendingEmail, _) ⇒
            maybePendingEmail.fold[Future[Result]](
              internalServerError()
            ) { pendingEmail ⇒
                sendVerificationRequest(pendingEmail, userInfo)
              }
        }

      def ifDE = {
        session: HTSSession ⇒
          {
            session.pendingEmail.fold[Future[Result]](
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

    checkSessionExists(ifDigital, ifDE)
  }(redirectOnLoginURL = routes.EmailController.verifyEmail.url)

  def verifyEmailError: Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒

    def ifDigital =
        checkEnrolledAndSession {
          case (info, _, _) ⇒
            info.email.fold(
              SeeOther(routes.EmailController.verifyEmailErrorTryLater().url)
            )(email ⇒ Ok(views.html.email.cannot_change_email(email, EmailVerificationErrorContinueForm.continueForm)))
        }

      def ifDE = {
        session: HTSSession ⇒
          {
            session.confirmedEmail.fold[Future[Result]](
              SeeOther(routes.EmailController.verifyEmailErrorTryLater().url)
            )(email ⇒
                Ok(views.html.email.cannot_change_email(email, EmailVerificationErrorContinueForm.continueForm)))
          }
      }

    checkSessionExists(ifDigital, ifDE)

  }(redirectOnLoginURL = routes.EmailController.verifyEmailError().url)

  def verifyEmailErrorTryLater: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    helpToSaveService.getUserEnrolmentStatus().fold(
      { e ⇒
        logger.warn(s"Could not check enrolment status: $e")
        internalServerError()
      }, { status ⇒
        status.fold(
          Ok(views.html.email.cannot_change_email_try_later(returningUser = false)),
          _ ⇒ Ok(views.html.email.cannot_change_email_try_later(returningUser = true))
        )
      }
    )
  }(redirectOnLoginURL = routes.EmailController.verifyEmailErrorTryLater().url)

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

  def verifyEmailErrorSubmit: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒

    def handleForm(email: Option[String]) =
        email.fold[Future[Result]](
          SeeOther(routes.EmailController.verifyEmailErrorTryLater().url)
        )(e ⇒
            EmailVerificationErrorContinueForm.continueForm.bindFromRequest().fold(
              form ⇒ Ok(views.html.email.cannot_change_email(e, form)),
              { continue ⇒
                if (continue.value) {
                  SeeOther(routes.EmailController.confirmEmail(crypto.encrypt(e)).url)
                } else {
                  SeeOther(routes.IntroductionController.getAboutHelpToSave().url)
                }
              }
            )
          )

      def ifDigital =
        checkEnrolledAndSession {
          case (info, _, _) ⇒
            handleForm(info.email)
        }

      def ifDE = (session: HTSSession) ⇒ handleForm(session.confirmedEmail)

    checkSessionExists(ifDigital, ifDE)

  }(redirectOnLoginURL = routes.EmailController.verifyEmailError().url)

  val getLinkExpiredPage: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    Ok(views.html.link_expired())
  }(redirectOnLoginURL = routes.EmailController.getLinkExpiredPage().url)

  def getEmailVerified: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒

    def ifDigital =
        checkEnrolledAndSession {
          case (info, _, confirmedEmail) ⇒
            confirmedEmail.fold[Future[Result]] {
              logger.warn("Could not find confirmed email", htsContext.nino)
              val url = info.email.fold(
                routes.EmailController.verifyEmailErrorTryLater().url)(
                  _ ⇒ routes.EmailController.verifyEmailError().url
                )
              toFuture(SeeOther(url))
            }(email ⇒ toFuture(Ok(views.html.email.email_updated(email))))
        }

      def ifDE = {
        session: HTSSession ⇒
          {
            session.confirmedEmail.fold[Future[Result]] {
              logger.warn("Could not find confirmed email in the session for DE user", htsContext.nino)
              internalServerError()
            }(email ⇒ toFuture(Ok(views.html.email.email_updated(email)))
            )
          }
      }

    checkSessionExists(ifDigital, ifDE)

  }(redirectOnLoginURL = routes.EmailController.getEmailVerified().url)

  def getEmailUpdated: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒

    def handle(email: Option[String]) =
        email.fold[Future[Result]](
          SeeOther(routes.EmailController.getGiveEmailPage().url))(
            email ⇒ Ok(views.html.email.email_updated(email)))

      def ifDigital =
        checkEnrolledAndSession {
          case (info, _, _) ⇒
            handle(info.email)
        }

      def ifDE = {
        session: HTSSession ⇒
          {
            handle(session.confirmedEmail)
          }
      }

    checkSessionExists(ifDigital, ifDE)

  }(redirectOnLoginURL = routes.EmailController.getEmailUpdated().url)

  def emailUpdatedSubmit: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    checkSessionExists(
      SeeOther(routes.RegisterController.getCreateAccountPage().url),
      _ ⇒ SeeOther(frontendAppConfig.nsiManageAccountUrl)
    )
  }(redirectOnLoginURL = routes.EmailController.emailUpdatedSubmit().url)

  private def checkSessionExists(ifDigital: ⇒ Future[Result], ifDE: HTSSession ⇒ Future[Result])(implicit hc: HeaderCarrier,
                                                                                                 request: Request[_]): Future[Result] = {

      def handleSessionError: String ⇒ Future[Result] = _ ⇒ internalServerError()

    sessionCacheConnector.get
      .leftSemiflatMap { error ⇒
        logger.warn(s"error retrieving the session from keystore, error = $error")
        handleSessionError(error)
      }.semiflatMap {
        case Some(session) ⇒
          if (session.isDigital) {
            ifDigital
          } else {
            ifDE(session)
          }

        case None ⇒
          logger.warn("expecting a valid session for the user during email submit, but not found")
          internalServerError()
      }.merge
  }
}

object EmailController {

  sealed trait EligibleInfo

  object EligibleInfo {

    case class EligibleWithEmail(userInfo: UserInfo, email: Email, confirmedEmail: Option[Email], eligible: Eligible) extends EligibleInfo

    case class EligibleWithNoEmail(userInfo: UserInfo, eligible: Eligible) extends EligibleInfo

  }

  private case class CannotCreateAccountReason(result: Either[AlreadyHasAccount, Ineligible])

  private object CannotCreateAccountReason {
    def apply(ineligible: Ineligible): CannotCreateAccountReason = CannotCreateAccountReason(Right(ineligible))

    def apply(alreadyHasAccount: AlreadyHasAccount): CannotCreateAccountReason = CannotCreateAccountReason(Left(alreadyHasAccount))
  }

}
