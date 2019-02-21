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

import com.google.inject.Inject
import javax.inject.Singleton
import play.api.i18n.MessagesApi
import play.api.mvc._
import play.api.{Configuration, Environment}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosavefrontend.auth.HelpToSaveAuth
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.Logging._
import uk.gov.hmrc.helptosavefrontend.util.{NINOLogMessageTransformer, toFuture}
import uk.gov.hmrc.helptosavefrontend.views

import scala.concurrent.ExecutionContext

@Singleton
class IntroductionController @Inject() (val authConnector:     AuthConnector,
                                        val metrics:           Metrics,
                                        val helpToSaveService: HelpToSaveService
)(implicit override val messagesApi: MessagesApi,
  val transformer:       NINOLogMessageTransformer,
  val frontendAppConfig: FrontendAppConfig,
  val config:            Configuration,
  val env:               Environment,
  ec:                    ExecutionContext)

  extends BaseController with HelpToSaveAuth with EnrolmentCheckBehaviour {

  private val baseUrl: String = frontendAppConfig.govUkURL

  def getAboutHelpToSave: Action[AnyContent] = unprotected { implicit request ⇒ implicit htsContext ⇒
    SeeOther(baseUrl)
  }

  def getEligibility: Action[AnyContent] = unprotected { implicit request ⇒ implicit htsContext ⇒
    SeeOther(s"$baseUrl/eligibility")
  }

  def getHowTheAccountWorks: Action[AnyContent] = unprotected { implicit request ⇒ implicit htsContext ⇒
    SeeOther(baseUrl)
  }

  def getHowWeCalculateBonuses: Action[AnyContent] = unprotected { implicit request ⇒ implicit htsContext ⇒
    SeeOther(s"$baseUrl/what-youll-get")
  }

  def getApply: Action[AnyContent] = unprotected { implicit request ⇒ implicit htsContext ⇒
    SeeOther(s"$baseUrl/how-to-apply")
  }

  def showPrivacyPage: Action[AnyContent] = unprotected { implicit request ⇒ implicit htsContext ⇒
    Ok(views.html.core.privacy())
  }

  def getHelpPage: Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    checkIfEnrolled({
      // not enrolled
      () ⇒ SeeOther(routes.AccessAccountController.getNoAccountPage().url)
    }, {
      e ⇒
        logger.warn(s"Could not check enrolment: $e", htsContext.nino)
        internalServerError()
    }, () ⇒
      Ok(views.html.helpinformation.help_information())
    )

  }(routes.IntroductionController.getAboutHelpToSave().url)
}
