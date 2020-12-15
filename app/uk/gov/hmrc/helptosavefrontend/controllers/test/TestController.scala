/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.helptosavefrontend.controllers.test

import com.google.inject.Inject
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosavefrontend.auth.HelpToSaveAuth
import uk.gov.hmrc.helptosavefrontend.config.{ErrorHandler, FrontendAppConfig}
import uk.gov.hmrc.helptosavefrontend.connectors.test.TestConnector
import uk.gov.hmrc.helptosavefrontend.controllers.{BaseController, CommonPlayDependencies, EnrolmentCheckBehaviour}
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.{MaintenanceSchedule, NINO, NINOLogMessageTransformer}
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException}

import scala.concurrent.ExecutionContext

class TestController @Inject()(
                                val helpToSaveService: HelpToSaveService,
                                val authConnector: AuthConnector,
                                 val metrics: Metrics,
                                cpd: CommonPlayDependencies,
                                maintenanceSchedule: MaintenanceSchedule,
                                mcc: MessagesControllerComponents,
                                errorHandler: ErrorHandler,
                                testConnector: TestConnector)(implicit ec: ExecutionContext,
                                                              val frontendAppConfig: FrontendAppConfig,

                                                              val transformer: NINOLogMessageTransformer,
                                                              val config: Configuration,
                                                              val env: Environment) extends
  BaseController(cpd, mcc, errorHandler, maintenanceSchedule) with HelpToSaveAuth with EnrolmentCheckBehaviour {


  def getHTSUser(nino: NINO): Action[AnyContent] = {
    authorisedForHtsWithNINO {
      implicit request =>
        implicit htsContext =>
          testConnector.getHtsUser(nino).map {
            s =>
              s.status match {
                case OK => Ok(s"Nino associated with the given NINO is: ${s.json}")
                case NOT_FOUND => Ok("Could not find hts user with the given NINO")
                case _ => Ok("Something went wrong")
              }
          }.recover {
            case _: NotFoundException => Ok("HTS user not found")
          }
    }(loginContinueURL = verifyLoginUrl)
  }

  def populateHTSReminders(noUsers: Int, emailPrefix: String, daysToReceive: List[Int]): Action[AnyContent] =
    authorisedForHtsWithNINO {
    implicit request =>
      implicit htsContext =>
        testConnector.populateReminders(noUsers, emailPrefix, daysToReceive).map {
          s =>
            s.status match {
              case OK => Ok("Reminders successfully populated")
              case _ => Ok("Something went wrong")
            }
        }.recover {
          case error => Ok(s"Unable to populate reminders: $error")
        }
  } (loginContinueURL = verifyLoginUrl)

}
