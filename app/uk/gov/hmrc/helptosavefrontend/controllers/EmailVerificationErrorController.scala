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

import cats.instances.future._
import com.google.inject.Inject
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosavefrontend.auth.HelptoSaveAuth
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.NINOLogMessageTransformer
import uk.gov.hmrc.helptosavefrontend.views

class EmailVerificationErrorController @Inject() (helpToSaveService: HelpToSaveService,
                                                  val authConnector: AuthConnector,
                                                  val metrics:       Metrics)(implicit override val messagesApi: MessagesApi,
                                                                              val transformer:       NINOLogMessageTransformer,
                                                                              val frontendAppConfig: FrontendAppConfig,
                                                                              val config:            Configuration,
                                                                              val env:               Environment)

  extends BaseController with HelptoSaveAuth {

  def verifyEmailErrorTryLater: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    helpToSaveService.getUserEnrolmentStatus().fold(
      { e ⇒
        logger.warn(s"Could not check enrolment status: $e")
        internalServerError()
      }, { status ⇒
        status.fold(
          Ok(views.html.core.cannot_change_email_try_later(returningUser = false)),
          _ ⇒ Ok(views.html.core.cannot_change_email_try_later(returningUser = true))
        )
      }
    )
  }(redirectOnLoginURL = routes.EmailVerificationErrorController.verifyEmailErrorTryLater().url)
}
