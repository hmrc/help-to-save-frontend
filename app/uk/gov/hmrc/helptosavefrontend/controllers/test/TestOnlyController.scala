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

import cats.instances.future._
import com.google.inject.{Inject, Singleton}
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.auth.HelpToSaveAuth
import uk.gov.hmrc.helptosavefrontend.config.{ErrorHandler, FrontendAppConfig}
import uk.gov.hmrc.helptosavefrontend.controllers.{BaseController, CommonPlayDependencies, SessionBehaviour}
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.repo.SessionStore
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveReminderService
import uk.gov.hmrc.helptosavefrontend.util.{Logging, NINOLogMessageTransformer}

import scala.concurrent.ExecutionContext
@Singleton
class TestOnlyController @Inject() (
  val helpToSaveReminderService: HelpToSaveReminderService,
  val sessionStore: SessionStore,
  val authConnector: AuthConnector,
  val metrics: Metrics,
  val auditor: HTSAuditor,
  cpd: CommonPlayDependencies,
  mcc: MessagesControllerComponents,
  errorHandler: ErrorHandler
)(
  val config: Configuration,
  val frontendAppConfig: FrontendAppConfig,
  implicit val transformer: NINOLogMessageTransformer,
  val env: Environment,
  implicit val ec: ExecutionContext
) extends BaseController(cpd, mcc, errorHandler) with HelpToSaveAuth with SessionBehaviour with Logging {

  def getHtsUser(nino: String): Action[AnyContent] =
    Action.async { implicit request ⇒
      helpToSaveReminderService
        .getHtsUser(nino)
        .fold(e ⇒ {
          NotFound
        }, { htsUser ⇒
          Ok(Json.toJson(htsUser))
        })
    }

}
