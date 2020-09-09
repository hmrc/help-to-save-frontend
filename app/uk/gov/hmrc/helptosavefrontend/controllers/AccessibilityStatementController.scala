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

package uk.gov.hmrc.helptosavefrontend.controllers

  import java.net.URLEncoder

  import akka.http.scaladsl.model.Uri
  import javax.inject.Inject
  import play.api.mvc.{Action, AnyContent, BodyParsers, Call, MessagesControllerComponents}
  import uk.gov.hmrc.helptosavefrontend.config.{ErrorHandler, FrontendAppConfig}
  import uk.gov.hmrc.helptosavefrontend.models.HtsContext
  import uk.gov.hmrc.helptosavefrontend.util.MaintenanceSchedule
  import uk.gov.hmrc.helptosavefrontend.views.html.accessibility.accessibility_statement

  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.Future

  class AccessibilityStatementController @Inject()(config: FrontendAppConfig,
                                                   parser: BodyParsers.Default,
                                                   htsContext: HtsContext,
                                                   cpd: CommonPlayDependencies,
                                                   mcc: MessagesControllerComponents,
                                                   errorHandler: ErrorHandler,
                                                   maintenanceSchedule: MaintenanceSchedule,
                                                   accessibility_statement: accessibility_statement) extends BaseController(cpd, mcc, errorHandler, maintenanceSchedule) with MessagesRequestHelper{

    def get: Action[AnyContent] = messagesAction(parser).async {
      implicit request =>

        val service = "Help To Save"

        val baseUrl = config.accessibilityStatementUrl

        val pageUrl = Uri(request.headers.get("referer").getOrElse(
          routes.AccessibilityStatementController.get().url
        ))

        val accessibilityUrl = s"${baseUrl}?service=$service&userAction=${
          URLEncoder.encode(pageUrl.path.toString(), "UTF-8")
        }"

        Future.successful(Ok(accessibility_statement(accessibilityUrl)(htsContext,request,request2Messages,appConfig)))
    }
  }
