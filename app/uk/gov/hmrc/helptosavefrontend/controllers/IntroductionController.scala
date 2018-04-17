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
import javax.inject.Singleton
import play.api.i18n.MessagesApi
import play.api.mvc._
import play.api.{Configuration, Environment}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosavefrontend.auth.HelptoSaveAuth
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.util.{NINOLogMessageTransformer, toFuture}
import uk.gov.hmrc.helptosavefrontend.views
import uk.gov.hmrc.play.bootstrap.controller.ActionWithMdc

@Singleton
class IntroductionController @Inject() (val authConnector: AuthConnector,
                                        val metrics:       Metrics)(implicit override val messagesApi: MessagesApi,
                                                                    val transformer:       NINOLogMessageTransformer,
                                                                    val frontendAppConfig: FrontendAppConfig,
                                                                    val config:            Configuration,
                                                                    val env:               Environment)

  extends BaseController with HelptoSaveAuth {

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

  def showPrivacyPage: Action[AnyContent] = unprotected { implicit request ⇒ implicit htsContext ⇒
    Ok(views.html.core.privacy())
  }

}
