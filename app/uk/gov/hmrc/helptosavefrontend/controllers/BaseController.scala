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

import com.google.inject.{Inject, Singleton}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{MessagesControllerComponents, Request, RequestHeader, Result}
import uk.gov.hmrc.helptosavefrontend.config.{ErrorHandler, FrontendAppConfig}
import uk.gov.hmrc.helptosavefrontend.util.MaintenanceSchedule
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

@Singleton
class BaseController @Inject() (
  cpd: CommonPlayDependencies,
  mcc: MessagesControllerComponents,
  errorHandler: ErrorHandler,
  maintenanceSchedule: MaintenanceSchedule
) extends FrontendController(mcc) with I18nSupport {

  override implicit val messagesApi: MessagesApi = cpd.messagesApi

  implicit val appConfig: FrontendAppConfig = cpd.appConfig

  implicit val maintenence: MaintenanceSchedule = maintenanceSchedule

  val Messages: MessagesApi = messagesApi

  implicit override def hc(implicit rh: RequestHeader): HeaderCarrier =
    HeaderCarrierConverter.fromHeadersAndSessionAndRequest(rh.headers, Some(rh.session), Some(rh))

  def internalServerError()(implicit request: Request[_]): Result =
    InternalServerError(errorHandler.internalServerErrorTemplate(request))
}

class CommonPlayDependencies @Inject() (val appConfig: FrontendAppConfig, val messagesApi: MessagesApi)
