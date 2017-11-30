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

import javax.inject.Singleton

import com.google.inject.Inject
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import uk.gov.hmrc.helptosavefrontend.config.FrontendAuthConnector
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.util.toFuture
import uk.gov.hmrc.helptosavefrontend.views
import uk.gov.hmrc.play.frontend.controller.ActionWithMdc

@Singleton
class IntroductionController @Inject() (val messagesApi:       MessagesApi,
                                        frontendAuthConnector: FrontendAuthConnector,
                                        metrics:               Metrics) extends HelpToSaveAuth(frontendAuthConnector, metrics) with I18nSupport {

  def getAboutHelpToSave: Action[AnyContent] = unprotected { implicit request ⇒ implicit htsContext ⇒
    Ok(views.html.introduction.about_help_to_save())
  }

  def getEligibility: Action[AnyContent] = unprotected { implicit request ⇒ implicit htsContext ⇒
    Ok(views.html.introduction.eligibility())
  }

  def getHowTheAccountWorks: Action[AnyContent] = unprotected { implicit request ⇒ implicit htsContext ⇒
    Ok(views.html.introduction.how_the_account_works())
  }

  def getHowWeCalculateBonuses: Action[AnyContent] = unprotected { implicit request ⇒ implicit htsContext ⇒
    Ok(views.html.introduction.how_we_calculate_bonuses())
  }

  def getApply: Action[AnyContent] = unprotected { implicit request ⇒ implicit htsContext ⇒
    Ok(views.html.introduction.apply())
  }

  def applySubmit: Action[AnyContent] = ActionWithMdc { _ ⇒
    SeeOther(routes.EligibilityCheckController.getCheckEligibility().url)
  }

}
