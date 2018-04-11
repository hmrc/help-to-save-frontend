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

import com.google.inject.Singleton
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.play.bootstrap.controller.{ActionWithMdc, FrontendController}

@Singleton
class ForbiddenController extends FrontendController {

  def forbidden: Action[AnyContent] = ActionWithMdc {
    Forbidden("Please ask the HtS Dev team for permissions to access this site")
  }

}
