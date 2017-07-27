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
import play.api.Application
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import uk.gov.hmrc.helptosavefrontend.views

import scala.concurrent.Future

@Singleton
class StartPagesController @Inject()(implicit app: Application, val messagesApi: MessagesApi) extends HelpToSaveAuth(app) with I18nSupport {

  def getApplyHelpToSave: Action[AnyContent] = unprotected {
    implicit request ⇒
      implicit htsContext ⇒
        Future.successful(Ok(views.html.core.apply_help_to_save()))
  }

  def getAboutHelpToSave: Action[AnyContent] = unprotected {
    implicit request ⇒
      implicit htsContext ⇒
        Future.successful(Ok(views.html.core.about_help_to_save()))
  }

  def getEligibilityHelpToSave: Action[AnyContent] = unprotected {
    implicit request ⇒
      implicit htsContext ⇒
        Future.successful(Ok(views.html.core.eligibility_help_to_save()))
  }

}
