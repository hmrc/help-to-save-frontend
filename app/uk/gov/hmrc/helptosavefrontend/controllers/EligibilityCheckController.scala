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

import cats.data.EitherT
import cats.instances.future._
import cats.syntax.either._
import com.google.inject.Inject
import play.api.Application
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig.personalAccountUrl
import uk.gov.hmrc.helptosavefrontend.connectors.SessionCacheConnector
import uk.gov.hmrc.helptosavefrontend.controllers.EnrolmentCheckBehaviour.EnrolmentServiceError
import uk.gov.hmrc.helptosavefrontend.models.UserInformationRetrievalError.MissingUserInfos
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.services.{EnrolmentService, HelpToSaveService, JSONSchemaValidationService}
import uk.gov.hmrc.helptosavefrontend.util.{HTSAuditor, Logging, NINO, UserDetailsURI, toFuture}
import uk.gov.hmrc.helptosavefrontend.views
import uk.gov.hmrc.play.config.AppName
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class EligibilityCheckController  @Inject()(val messagesApi: MessagesApi,
                                            helpToSaveService: HelpToSaveService,
                                            val sessionCacheConnector: SessionCacheConnector,
                                            jsonSchemaValidationService: JSONSchemaValidationService,
                                            val enrolmentService: EnrolmentService,
                                            val app: Application,
                                            auditor: HTSAuditor)(implicit ec: ExecutionContext)
  extends HelpToSaveAuth(app) with EnrolmentCheckBehaviour with SessionBehaviour with I18nSupport with Logging with AppName {

  import EligibilityCheckController._

  def getCheckEligibility: Action[AnyContent] =  authorisedForHtsWithInfo {
    implicit request ⇒
      implicit htsContext ⇒
        checkIfAlreadyEnrolled({
          nino ⇒
            checkSession(
              // there is no session yet
              performEligibilityChecks(nino, htsContext).fold(
                handleEligibilityCheckError,
                r ⇒ handleEligibilityResult(r, nino)
              ), session ⇒
                // there is a session
                session.eligibilityCheckResult.fold(
                  SeeOther(routes.EligibilityCheckController.notEligible().url)
                )( _ ⇒
                  SeeOther(routes.EligibilityCheckController.getIsEligible().url)
                )
            )
        },
          handleEnrolmentCheckErrorOnEligibilityCheck
        )
  }

  val notEligible: Action[AnyContent] = authorisedForHtsWithInfo {
    implicit request ⇒
      implicit htsContext ⇒
        checkIfAlreadyEnrolled { _ ⇒
          checkSession(
            SeeOther(routes.EligibilityCheckController.getCheckEligibility().url),
            _.eligibilityCheckResult.fold {
              Ok(views.html.core.not_eligible())
            }(_ ⇒
              SeeOther(routes.EligibilityCheckController.getIsEligible().url)
            )
          )
        }
  }

  val getIsEligible: Action[AnyContent] = authorisedForHtsWithInfo {
    implicit request ⇒
      implicit htsContext ⇒
        checkIfAlreadyEnrolled { _ ⇒
          checkSession(
            SeeOther(routes.EligibilityCheckController.getCheckEligibility().url),
            _.eligibilityCheckResult.fold(
              SeeOther(routes.EligibilityCheckController.notEligible().url)
            )(_ ⇒
              Ok(views.html.register.you_are_eligible())
            )
          )
        }
  }

  private def handleEnrolmentCheckErrorOnEligibilityCheck(enrolmentCheckError: EnrolmentServiceError
                                                         )(implicit hc: HeaderCarrier): Future[Result] =
  // if we couldn't check the enrolment check the eligibility to see if they already have an account
    helpToSaveService.checkEligibility(enrolmentCheckError.nino).fold(
      { e ⇒
        logger.warn(s"Could not check eligibility $e")
        InternalServerError
      },
      result ⇒
        result.result match {
          case Left(IneligibilityReason.AccountAlreadyOpened) ⇒
            Ok("You've already got an account - yay!")

          case _ ⇒
            InternalServerError
        }
    )

  private def performEligibilityChecks(nino: NINO,
                                       htsContext: HtsContext
                                      )(implicit hc: HeaderCarrier): EitherT[Future, Error, EligibilityResultWithUserInfo] =
    for {
      userDetailsURI ← EitherT.fromOption[Future](htsContext.userDetailsURI, Error("Could not find user details URI"))
      eligible       ← helpToSaveService.checkEligibility(nino).leftMap(Error.apply)
      resultWithInfo ← getUserInformation(eligible, nino, userDetailsURI)
      nsiUserInfo    = resultWithInfo.value.toOption.map(_._2)
      _              ← EitherT.fromEither[Future](validateCreateAccountJsonSchema(nsiUserInfo)).leftMap(Error.apply)
      session        = HTSSession(nsiUserInfo)
      _              ←  sessionCacheConnector.put(session).leftMap[Error](Error.apply)
    } yield resultWithInfo

  private def getUserInformation(eligibilityCheckResult: EligibilityCheckResult,
                                nino: NINO,
                                userDetailsURI: UserDetailsURI
                               )(implicit hc: HeaderCarrier): EitherT[Future,Error,EligibilityResultWithUserInfo] =
    eligibilityCheckResult.result.fold[EitherT[Future,Error,EligibilityResultWithUserInfo]](
      { ineligibilityReason ⇒
        // if the person is ineligible don't get the user info - return with an ineligibility reason
        EitherT.pure[Future,Error,EligibilityResultWithUserInfo](EligibilityResultWithUserInfo(Left(ineligibilityReason)))
      }, { eligibilityReason ⇒
          helpToSaveService.getUserInformation(nino, userDetailsURI).bimap(
            Error.apply,
            userInfo ⇒ EligibilityResultWithUserInfo(Right(eligibilityReason → NSIUserInfo(userInfo)))
          )
      }
    )

  private def handleEligibilityResult(result: EligibilityResultWithUserInfo,
                                      nino: NINO
                                     )(implicit hc: HeaderCarrier) = {
    result.value.fold(
      { ineligibilityReason ⇒
        auditor.sendEvent(new EligibilityCheckEvent(appName, nino, Some(ineligibilityReason.legibleString)))
        SeeOther(routes.EligibilityCheckController.notEligible().url)
    },{ case (eligibilityReason, nsiUserInfo) ⇒
      auditor.sendEvent(new EligibilityCheckEvent(appName, nino, None))
      SeeOther(routes.EligibilityCheckController.getIsEligible().url)
    })
  }

  private def handleEligibilityCheckError(error: Error)
                                         (implicit request: Request[AnyContent],
                                          hc: HeaderCarrier,
                                          htsContext: HtsContext): Result = error.value match {

    case Left(error) ⇒
      logger.warn(error)
      InternalServerError

    case Right(u: UserInformationRetrievalError) ⇒
      u match {

        case UserInformationRetrievalError.BackendError(message, nino) ⇒
          logger.warn(s"An error occurred while trying to call the backend service to get user information $nino: $message")
          InternalServerError

        case MissingUserInfos(missingInfo, nino) =>
          val problemDescription = s"user $nino has missing information: ${missingInfo.mkString(",")}"
          logger.warn(problemDescription)
          auditor.sendEvent(new EligibilityCheckEvent(appName, nino, Some(problemDescription)))
          Ok(views.html.register.missing_user_info(missingInfo, personalAccountUrl))

      }
  }

  private def validateCreateAccountJsonSchema(userInfo: Option[NSIUserInfo]): Either[String, Option[NSIUserInfo]] = {
    import uk.gov.hmrc.helptosavefrontend.util.Toggles._

    userInfo match {
      case None => Right(None)
      case Some(ui) =>
        FEATURE[Either[String, Option[NSIUserInfo]]]("outgoing-json-validation", app.configuration, Right(userInfo)) enabled() thenDo {
          jsonSchemaValidationService.validate(Json.toJson(ui)).map(_ ⇒ Some(ui))
        }
    }
  }

}

object EligibilityCheckController {

  private case class Error(value: Either[String,UserInformationRetrievalError])

  private object Error{
    def apply(error: String): Error = Error(Left(error))
    def apply(u: UserInformationRetrievalError): Error = Error(Right(u))
  }

  private case class EligibilityResultWithUserInfo(value: Either[IneligibilityReason,(EligibilityReason,NSIUserInfo)])
}
