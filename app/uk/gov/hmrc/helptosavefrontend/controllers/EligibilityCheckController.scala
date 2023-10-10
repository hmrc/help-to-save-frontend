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

package uk.gov.hmrc.helptosavefrontend.controllers

import cats.data.EitherT
import cats.instances.future._
import cats.instances.option._
import cats.syntax.traverse._
import com.google.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Request, Result => PlayResult}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosavefrontend.auth.HelpToSaveAuth
import uk.gov.hmrc.helptosavefrontend.config.{ErrorHandler, FrontendAppConfig}
import uk.gov.hmrc.helptosavefrontend.controllers.SessionBehaviour.SessionWithEligibilityCheck
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.models.HTSSession.EligibleWithUserInfo
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityCheckResultType.{Eligible, Ineligible}
import uk.gov.hmrc.helptosavefrontend.models.eligibility.{EligibilityCheckResultType, IneligibilityReason}
import uk.gov.hmrc.helptosavefrontend.models.userinfo.UserInfo
import uk.gov.hmrc.helptosavefrontend.repo.SessionStore
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.Logging._
import uk.gov.hmrc.helptosavefrontend.util.{MaintenanceSchedule, NINOLogMessageTransformer, Result, toFuture}
import uk.gov.hmrc.helptosavefrontend.views.html.register.{missing_user_info, not_eligible, think_you_are_eligible, you_are_eligible}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class EligibilityCheckController @Inject() (
  val helpToSaveService: HelpToSaveService,
  val sessionStore: SessionStore,
  val authConnector: AuthConnector,
  val metrics: Metrics,
  cpd: CommonPlayDependencies,
  mcc: MessagesControllerComponents,
  errorHandler: ErrorHandler,
  maintenanceSchedule: MaintenanceSchedule,
  notEligible: not_eligible,
  youAreEligible: you_are_eligible,
  missingUserInfo: missing_user_info,
  thinkYouAreEligible: think_you_are_eligible
)(
  implicit val transformer: NINOLogMessageTransformer,
  val frontendAppConfig: FrontendAppConfig,
  val config: Configuration,
  val env: Environment,
  ec: ExecutionContext
) extends BaseController(cpd, mcc, errorHandler, maintenanceSchedule) with HelpToSaveAuth with EnrolmentCheckBehaviour
    with SessionBehaviour with CapCheckBehaviour {

  val earlyCapCheckOn: Boolean = frontendAppConfig.earlyCapCheckOn

  def getCheckEligibility: Action[AnyContent] =
    authorisedForHtsWithInfo { implicit request => implicit htsContext => // scalastyle:ignore
      def eligibilityAction(session: Option[HTSSession]): Future[PlayResult] =
        if (earlyCapCheckOn) {
          logger.info("Checking pre-eligibility cap for nino", htsContext.nino)
          checkIfAccountCreateAllowed(getEligibilityActionResult(session))
        } else {
          getEligibilityActionResult(session)
        }

      def handleSessionAndEnrolmentStatus(
        maybeSession: Option[HTSSession],
        enrolmentStatus: Option[EnrolmentStatus]
      ): Future[PlayResult] =
        (maybeSession, enrolmentStatus) match {
          case (s, Some(EnrolmentStatus.Enrolled(itmpHtSFlag))) =>
            if (!itmpHtSFlag) {
              setItmpFlag(htsContext.nino)
            }
            SeeOther(s.flatMap(_.attemptedAccountHolderPageURL).getOrElse(appConfig.nsiManageAccountUrl))

          case (s, _) =>
            s.flatMap(_.eligibilityCheckResult).fold(eligibilityAction(s)) {
              _.fold(
                _ => SeeOther(routes.EligibilityCheckController.getIsNotEligible.url), // user is not eligible
                _ => SeeOther(routes.EligibilityCheckController.getIsEligible.url) // user is eligible
              )
            }
        }

      def getEnrolmentStatus: Future[Option[EnrolmentStatus]] =
        helpToSaveService
          .getUserEnrolmentStatus()
          .bimap(
            { e =>
              logger.warn(s"Could not check enrolment status: $e")
              None: Option[EnrolmentStatus]
            },
            Some(_)
          )
          .merge

      val result = for {
        session         <- sessionStore.get
        enrolmentStatus <- EitherT.liftF(getEnrolmentStatus)
        eligibilityResult <- EitherT.liftF[Future, String, PlayResult](
                              handleSessionAndEnrolmentStatus(session, enrolmentStatus)
                            )
      } yield eligibilityResult

      result
        .leftMap[PlayResult]({ e =>
          logger.warn(s"Could not check eligibility: $e")
          internalServerError()
        })
        .merge
    }(loginContinueURL = routes.EligibilityCheckController.getCheckEligibility.url)

  def getIsNotEligible: Action[AnyContent] =
    authorisedForHtsWithNINO { implicit request => implicit htsContext =>
      checkIfAlreadyEnrolled { () =>
        checkHasDoneEligibilityChecks {
          SeeOther(routes.EligibilityCheckController.getCheckEligibility.url)
        } {
          _.eligibilityResult.fold(
            { ineligibleReason =>
              val ineligibilityType = IneligibilityReason.fromIneligible(ineligibleReason)
              val threshold = ineligibleReason.value.threshold
              ineligibilityType.fold {
                logger.warn(s"Could not parse ineligibility reason: $ineligibleReason", htsContext.nino)
                internalServerError()
              } { i =>
                Ok(notEligible(i, threshold))
              }
            },
            _ => SeeOther(routes.EligibilityCheckController.getIsEligible.url)
          )
        }
      }
    }(loginContinueURL = routes.EligibilityCheckController.getIsNotEligible.url)

  def getIsEligible: Action[AnyContent] =
    authorisedForHtsWithNINO { implicit request => implicit htsContext =>
      checkIfAlreadyEnrolled { () =>
        checkHasDoneEligibilityChecks {
          SeeOther(routes.EligibilityCheckController.getCheckEligibility.url)
        } {
          _.eligibilityResult.fold(
            _ => SeeOther(routes.EligibilityCheckController.getIsNotEligible.url),
            eligibleWithUserInfo => Ok(youAreEligible(eligibleWithUserInfo.userInfo))
          )
        }
      }
    }(loginContinueURL = routes.EligibilityCheckController.getCheckEligibility.url)

  def youAreEligibleSubmit: Action[AnyContent] =
    authorisedForHtsWithNINO { implicit request => implicit htsContext =>
      checkHasDoneEligibilityChecks {
        SeeOther(routes.EligibilityCheckController.getCheckEligibility.url)
      } {
        _.eligibilityResult.fold(
          _ => SeeOther(routes.EligibilityCheckController.getIsNotEligible.url), { userInfo =>
            val url = userInfo.userInfo.email
              .fold(
                routes.EmailController.getGiveEmailPage
              )(_ => routes.EmailController.getSelectEmailPage)
              .url
            SeeOther(url)
          }
        )
      }
    }(loginContinueURL = routes.EligibilityCheckController.youAreEligibleSubmit.url)

  def getMissingInfoPage: Action[AnyContent] =
    authorisedForHtsWithInfo { implicit request => implicit htsContext =>
      htsContext.userDetails.fold(
        missingInfo => Ok(missingUserInfo(missingInfo.missingInfo)),
        _ => SeeOther(routes.EligibilityCheckController.getCheckEligibility.url)
      )
    }(loginContinueURL = routes.EligibilityCheckController.getCheckEligibility.url)

  def getThinkYouAreEligiblePage: Action[AnyContent] =
    authorisedForHtsWithNINO { implicit request => implicit htsContext =>
      checkHasDoneEligibilityChecks {
        SeeOther(routes.EligibilityCheckController.getCheckEligibility.url)
      } {
        _.eligibilityResult.fold(
          _ => Ok(thinkYouAreEligible()),
          _ => SeeOther(routes.EligibilityCheckController.getIsEligible.url)
        )
      }
    }(loginContinueURL = routes.EligibilityCheckController.getThinkYouAreEligiblePage.url)

  private def getEligibilityActionResult(session: Option[HTSSession])(
    implicit hc: HeaderCarrier,
    htsContext: HtsContextWithNINOAndUserDetails,
    request: Request[AnyContent]
  ): Future[PlayResult] =
    htsContext.userDetails.fold[Future[PlayResult]](
      { missingUserInfo =>
        logger.warn(s"User has missing information: ${missingUserInfo.missingInfo.mkString(",")}", missingUserInfo.nino)
        SeeOther(routes.EligibilityCheckController.getMissingInfoPage.url)
      }, { userInfo =>
        performEligibilityChecks(userInfo).fold(
          { e =>
            logger.warn(e, htsContext.nino)
            internalServerError()
          },
          handleEligibilityResult(_, session)
        )
      }
    )

  private def performEligibilityChecks(userInfo: UserInfo)(
    implicit
    hc: HeaderCarrier
  ): EitherT[Future, String, EligibilityCheckResultType] =
    for {
      eligible <- helpToSaveService.checkEligibility()
      session = {
        val result = eligible.fold[Option[Either[Ineligible, EligibleWithUserInfo]]](
          e => Some(Right(EligibleWithUserInfo(Eligible(e), userInfo))),
          ineligible => Some(Left(Ineligible(ineligible))),
          _ => None
        )
        result.map(r => HTSSession(Some(r), None, None))
      }
      _ <- session.map(sessionStore.store).traverse[Result, Unit](identity)
    } yield eligible

  private def handleEligibilityResult(
    result: EligibilityCheckResultType,
    session: Option[HTSSession]
  )(implicit htsContext: HtsContextWithNINOAndUserDetails, hc: HeaderCarrier): PlayResult = {
    val nino = htsContext.nino
    result.fold(
      _ => SeeOther(routes.EligibilityCheckController.getIsEligible.url),
      _ => SeeOther(routes.EligibilityCheckController.getIsNotEligible.url),
      _ => {
        helpToSaveService.setITMPFlagAndUpdateMongo().value.onComplete {
          case Failure(e) =>
            logger.warn(s"error in setting ITMP flag and updating mongo, future failed: ${e.getMessage}", nino)
          case Success(Left(e))  => logger.warn(s"error in setting ITMP flag and updating mongo: $e", nino)
          case Success(Right(_)) => logger.info(s"Successfully set ITMP flag and updated mongo for user", nino)
        }

        val redirectTo =
          session.flatMap(_.attemptedAccountHolderPageURL).getOrElse(frontendAppConfig.nsiManageAccountUrl)
        SeeOther(redirectTo)
      }
    )
  }

  private def checkHasDoneEligibilityChecks(
    noSession: => Future[PlayResult]
  )(hasDoneChecks: SessionWithEligibilityCheck => Future[PlayResult])(
    implicit
    htsContext: HtsContextWithNINO,
    hc: HeaderCarrier,
    request: Request[AnyContent],
    transformer: NINOLogMessageTransformer
  ): Future[PlayResult] =
    checkSession(noSession) { session =>
      session.eligibilityCheckResult.fold[Future[PlayResult]](
        SeeOther(routes.EligibilityCheckController.getCheckEligibility.url)
      )(result => hasDoneChecks(SessionWithEligibilityCheck(result, session.pendingEmail, session.confirmedEmail)))
    }

}
