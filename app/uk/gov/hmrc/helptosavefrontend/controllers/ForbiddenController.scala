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

import com.google.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.helptosavefrontend.config.{ErrorHandler, FrontendAppConfig}

@Singleton
class ForbiddenController @Inject() (cpd:          CommonPlayDependencies,
                                     mcc:          MessagesControllerComponents,
                                     errorHandler: ErrorHandler)(implicit appConfig: FrontendAppConfig)
  extends BaseController(cpd, mcc, errorHandler) {

  def forbidden: Action[AnyContent] = Action {
    Forbidden("Please ask the HtS Dev team for permissions to access this site")
  }

}
