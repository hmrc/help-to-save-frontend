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

import cats.instances.future._
import com.google.inject.Inject
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.helptosavefrontend.config.FrontendAuthConnector
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.NINOLogMessageTransformer
import uk.gov.hmrc.helptosavefrontend.views

class EmailVerificationErrorController @Inject() (helpToSaveService:     HelpToSaveService,
                                                  frontendAuthConnector: FrontendAuthConnector,
                                                  metrics:               Metrics)(
    implicit
    val messagesApi: MessagesApi,
    transformer:     NINOLogMessageTransformer
) extends HelpToSaveAuth(frontendAuthConnector, metrics) with I18nSupport {

  def verifyEmailErrorTryLater: Action[AnyContent] = authorisedForHtsWithNINO{ implicit request ⇒ implicit htsContext ⇒
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
