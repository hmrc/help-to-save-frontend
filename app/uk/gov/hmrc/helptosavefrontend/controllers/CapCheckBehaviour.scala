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

import play.api.mvc.Result
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.{Logging, toFuture}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

trait CapCheckBehaviour {
  this: BaseController with Logging ⇒

  val helpToSaveService: HelpToSaveService

  def checkIfAccountCreateAllowed(ifAllowed: ⇒ Future[Result])(implicit hc: HeaderCarrier): Future[Result] = {
    helpToSaveService.isAccountCreationAllowed().value.flatMap {
      _.fold(
        error ⇒ {
          logger.warn(s"Could not check if account create is allowed, due to: $error")
          ifAllowed
        }, { userCapResponse ⇒
          if (userCapResponse.isTotalCapDisabled && userCapResponse.isDailyCapDisabled) {
            SeeOther(routes.RegisterController.getServiceUnavailablePage().url)
          } else if (userCapResponse.isTotalCapDisabled || userCapResponse.isTotalCapReached) {
            SeeOther(routes.RegisterController.getTotalCapReachedPage().url)
          } else if (userCapResponse.isDailyCapDisabled || userCapResponse.isDailyCapReached) {
            SeeOther(routes.RegisterController.getDailyCapReachedPage().url)
          } else {
            ifAllowed
          }
        }
      )
    }
  }

}
