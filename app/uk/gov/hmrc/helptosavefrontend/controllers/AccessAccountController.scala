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

import com.google.inject.Inject
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.helptosavefrontend.config.{FrontendAppConfig, FrontendAuthConnector}
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.{Logging, toFuture}
import uk.gov.hmrc.helptosavefrontend.util.Logging._
import uk.gov.hmrc.helptosavefrontend.views

class AccessAccountController @Inject() (val messagesApi:       MessagesApi,
                                         val helpToSaveService: HelpToSaveService,
                                         frontendAuthConnector: FrontendAuthConnector,
                                         metrics:               Metrics)
  extends HelpToSaveAuth(frontendAuthConnector, metrics) with I18nSupport with Logging
  with EnrolmentCheckBehaviour {

  def accessAccount: Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒
    checkIfAlreadyEnrolled({
      // not enrolled
      _ ⇒ Ok(views.html.confirm_check_eligibility())
    }, {
      e ⇒
        logger.warn(s"Could not check enrolment (${e.message}) - proceeding to check eligibility", e.nino)
        SeeOther(routes.EligibilityCheckController.getCheckEligibility().url)
    }
    )
  }(redirectOnLoginURL = FrontendAppConfig.accessAccountUrl)

}
