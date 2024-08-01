/*
 * Copyright 2024 HM Revenue & Customs
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

import com.google.inject.Inject
import play.api.{Configuration, Environment}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.auth.HelpToSaveAuth
import uk.gov.hmrc.helptosavefrontend.config.{ErrorHandler, FrontendAppConfig}
import uk.gov.hmrc.helptosavefrontend.connectors.EmailVerificationConnector
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.repo.SessionStore
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, Logging, MaintenanceSchedule, NINOLogMessageTransformer, toFuture}
import uk.gov.hmrc.helptosavefrontend.views.html.email.accountholder.check_your_details

import java.time.format.DateTimeFormatter.{ofLocalizedDate, ofLocalizedDateTime}
import java.time.format.FormatStyle
import scala.concurrent.ExecutionContext

class CheckYourDetailsController @Inject() (
  val helpToSaveService: HelpToSaveService,
  val authConnector: AuthConnector,
  val emailVerificationConnector: EmailVerificationConnector,
  val metrics: Metrics,
  val auditor: HTSAuditor,
  val sessionStore: SessionStore,
  cpd: CommonPlayDependencies,
  mcc: MessagesControllerComponents,
  errorHandler: ErrorHandler,
  maintenanceSchedule: MaintenanceSchedule,
  checkYourDetails: check_your_details
)(
  implicit
  val crypto: Crypto,
  val transformer: NINOLogMessageTransformer,
  val frontendAppConfig: FrontendAppConfig,
  val config: Configuration,
  val env: Environment,
  ec: ExecutionContext
) extends BaseController(cpd, mcc, errorHandler, maintenanceSchedule) with HelpToSaveAuth with VerifyEmailBehaviour
    with EnrolmentCheckBehaviour with Logging {

  def checkYourDetails: Action[AnyContent] =
    authorisedForHtsWithInfo { implicit request => implicit htsContext =>
      htsContext.userDetails match {
        case Left(missingUserInfos) =>
          logger.warn(s"user data missing: $missingUserInfos")
          SeeOther(routes.EligibilityCheckController.getMissingInfoPage.url)
        case Right(userInfo) =>
          userInfo.address.postcode -> userInfo.address.lines match {
            case None -> _ =>
              logger.warn("user data is missing postcode")
              SeeOther(routes.EligibilityCheckController.getMissingInfoPage.url)

            case Some(postcode) -> (firstLine :: _ :: thirdLine :: tail) =>
              val formattedDateOfBirth = userInfo.dateOfBirth.format(ofLocalizedDate(FormatStyle.MEDIUM))
              Ok(checkYourDetails(userInfo, postcode, formattedDateOfBirth, firstLine, thirdLine))

            case _ =>
              logger.warn("missing address lines")
              SeeOther(routes.EligibilityCheckController.getMissingInfoPage.url)
          }
      } //TODO this doesn't do anything to check the lines are correct in the address - i.e line 3 isn't forced to be a city - check if this is checked elsewhere
    }(loginContinueURL = routes.AccountHolderController.onSubmit.url)
}
