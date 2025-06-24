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

import cats.instances.future._
import com.google.inject.Inject
import play.api.mvc._
import play.api.{Configuration, Environment}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosavefrontend.auth.HelpToSaveAuth
import uk.gov.hmrc.helptosavefrontend.config.{ErrorHandler, FrontendAppConfig}
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.Logging._
import uk.gov.hmrc.helptosavefrontend.util.{MaintenanceSchedule, NINOLogMessageTransformer, toFuture}
import uk.gov.hmrc.helptosavefrontend.views.html.core.privacy
import uk.gov.hmrc.helptosavefrontend.views.html.helpinformation.help_information
import uk.gov.hmrc.helptosavefrontend.views.html.time_out

import java.util.UUID
import javax.inject.Singleton
import scala.concurrent.ExecutionContext

@Singleton
class IntroductionController @Inject() (
  val authConnector: AuthConnector,
  val metrics: Metrics,
  val helpToSaveService: HelpToSaveService,
  cpd: CommonPlayDependencies,
  mcc: MessagesControllerComponents,
  errorHandler: ErrorHandler,
  maintenanceSchedule: MaintenanceSchedule,
  privacyView: privacy,
  helpInformationView: help_information,
  timeOutView: time_out
)(
  implicit val transformer: NINOLogMessageTransformer,
  val frontendAppConfig: FrontendAppConfig,
  val config: Configuration,
  val env: Environment,
  ec: ExecutionContext
) extends CustomBaseController(cpd, mcc, errorHandler, maintenanceSchedule) with HelpToSaveAuth
    with EnrolmentCheckBehaviour {

  private val baseUrl: String = frontendAppConfig.govUkURL

  def timedOut: Action[AnyContent] = unprotected { implicit request => implicit htsContext =>
    Ok(timeOutView())
  }

  def keepAlive: Action[AnyContent] = unprotected { _ => _ =>
    Ok("")
  }

  def getAboutHelpToSave: Action[AnyContent] = unprotected { _ => _ =>
    SeeOther(baseUrl)
  }

  def getEligibility: Action[AnyContent] = unprotected { _ => _ =>
    SeeOther(s"$baseUrl/eligibility")
  }

  def getHowTheAccountWorks: Action[AnyContent] = unprotected { _ => _ =>
    SeeOther(baseUrl)
  }

  def getHowWeCalculateBonuses: Action[AnyContent] = unprotected { _ => _ =>
    SeeOther(s"$baseUrl/what-youll-get")
  }

  def getApply: Action[AnyContent] = unprotected { _ => _ =>
    SeeOther(s"$baseUrl/how-to-apply")
  }

  def showPrivacyPage: Action[AnyContent] = unprotected { implicit request => implicit htsContext =>
    Ok(privacyView())
  }

  def getHelpPage: Action[AnyContent] =
    authorisedForHtsWithNINO { implicit request => implicit htsContext =>
      checkIfEnrolled(
        {
          // not enrolled
          () =>
            SeeOther(routes.AccessAccountController.getNoAccountPage.url)
        }, { e =>
          logger.warn(s"Could not check enrolment: $e", htsContext.nino)
          internalServerError()
        },
        () =>
          for {
            // get account
            account <- helpToSaveService
                        .getAccount(htsContext.nino, UUID.randomUUID())
                        .fold(e => {
                          logger.warn(s"error retrieving Account details from NS&I, error = $e", htsContext.nino)
                          None
                        }, { account =>
                          Some(account)
                        })
            //get account number
            accountNumber <- helpToSaveService
                              .getAccountNumber()
                              .fold(
                                e => {
                                  logger
                                    .warn(s"error retrieving Account details from NS&I, error = $e", htsContext.nino)
                                  None
                                }, { accountNumber =>
                                  accountNumber.accountNumber
                                }
                              )
          } yield Ok(helpInformationView(accountNumber, account))
      )

    }(routes.IntroductionController.getAboutHelpToSave.url)
}
