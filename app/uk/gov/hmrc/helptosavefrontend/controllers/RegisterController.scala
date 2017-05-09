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

import cats.instances.future._
import com.google.inject.Inject
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.helptosavefrontend.models.UserInfo
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.Result
import uk.gov.hmrc.helptosavefrontend.views
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

@Singleton
class RegisterController @Inject()(val messagesApi: MessagesApi, htsService: HelpToSaveService)
  extends HelpToSaveController with I18nSupport {

  def declaration: Action[AnyContent] = Action.async { implicit request ⇒
    authorisedForHts { (uri, nino) =>
      validateUser(uri, nino).fold(
        error ⇒ {
          Logger.error(s"Could not perform eligibility check: $error")
          InternalServerError("")
        }, _.fold(
          Ok(views.html.core.not_eligible()))(
          userDetails ⇒ Ok(views.html.register.declaration(userDetails))
        )
      )
    }
  }

  def getCreateAccountHelpToSave: Action[AnyContent] = Action.async { implicit request ⇒
    authorisedForHts {
      Future.successful(Ok(views.html.register.create_account_help_to_save()))
    }
  }

  /**
    * Does the following:
    * - get the user's NINO
    * - get the user's information
    * - check's if the user is eligible for HTS
    *
    * This returns a defined [[UserInfo]] if all of the above has successfully
    * been performed and the eligibility check is positive. This returns [[None]]
    * if all the above has successfully been performed and the eligibility check is negative.
    */
  private def validateUser(userDetailsUri: String, nino: String)(implicit hc: HeaderCarrier): Result[Option[UserInfo]] = for {
    userInfo ← htsService.getUserInfo(userDetailsUri, nino)
    eligible ← htsService.checkEligibility(nino)
  } yield eligible.fold(None, Some(userInfo))

}
