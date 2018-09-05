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

import com.google.inject.Inject
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosavefrontend.auth.HelpToSaveAuth
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.Logging._
import uk.gov.hmrc.helptosavefrontend.util.{NINOLogMessageTransformer, toFuture}
import uk.gov.hmrc.helptosavefrontend.views

class AccessAccountController @Inject() (val helpToSaveService: HelpToSaveService,
                                         val authConnector:     AuthConnector,
                                         val metrics:           Metrics)(implicit override val messagesApi: MessagesApi,
                                                                         val transformer:       NINOLogMessageTransformer,
                                                                         val frontendAppConfig: FrontendAppConfig,
                                                                         val config:            Configuration,
                                                                         val env:               Environment)
  extends BaseController with HelpToSaveAuth with EnrolmentCheckBehaviour {

  def getSignInPage: Action[AnyContent] = unprotected { implicit request ⇒ implicit htsContext ⇒
    if (appConfig.startPageRedirectionEnabled) {
      SeeOther("https://www.gov.uk/sign-in-help-to-save")
    } else {
      Ok(views.html.sign_in())
    }
  }

  def accessAccount: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    checkIfAlreadyEnrolled({
      // not enrolled
      () ⇒ SeeOther(routes.AccessAccountController.getNoAccountPage().url)
    }, {
      e ⇒
        logger.warn(s"Could not check enrolment ($e) - proceeding to check eligibility", htsContext.nino)
        SeeOther(routes.EligibilityCheckController.getCheckEligibility().url)
    }
    )
  }(redirectOnLoginURL = frontendAppConfig.accessAccountUrl)

  def getNoAccountPage: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    checkIfAlreadyEnrolled({
      () ⇒ Ok(views.html.core.confirm_check_eligibility())
    }, { _ ⇒
      SeeOther(routes.AccessAccountController.accessAccount().url)
    }
    )
  }(redirectOnLoginURL = routes.AccessAccountController.getNoAccountPage().url)

}
