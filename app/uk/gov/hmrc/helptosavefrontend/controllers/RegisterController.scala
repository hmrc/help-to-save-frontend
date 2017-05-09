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

import cats.data.EitherT
import cats.instances.future._
import com.google.inject.Inject
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.Action
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.Retrievals.userDetailsUri
import uk.gov.hmrc.auth.core.{AuthConnector, AuthProviders, AuthorisedFunctions, Enrolment}
import uk.gov.hmrc.helptosavefrontend.config.FrontendAuthConnector
import uk.gov.hmrc.helptosavefrontend.connectors.{CitizenDetailsConnector, EligibilityConnector}
import uk.gov.hmrc.helptosavefrontend.models.UserInfo
import uk.gov.hmrc.helptosavefrontend.services.userinfo.UserInfoService
import uk.gov.hmrc.helptosavefrontend.util.Result
import uk.gov.hmrc.helptosavefrontend.views
import uk.gov.hmrc.play.frontend.controller.FrontendController
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

@Singleton
class RegisterController @Inject()(val messagesApi: MessagesApi,
                                   eligibilityConnector: EligibilityConnector,
                                   citizenDetailsConnector: CitizenDetailsConnector)
  extends FrontendController with I18nSupport with AuthorisedFunctions {

  override def authConnector: AuthConnector = FrontendAuthConnector

  val userInfoService = new UserInfoService(citizenDetailsConnector)

  def declaration = Action.async { implicit request ⇒

    authorised(/* Enrolment("IR-SA") and */ AuthProviders(GovernmentGateway)).retrieve(userDetailsUri) { uri =>
      validateUser(uri).fold(
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

  def getCreateAccountHelpToSave = Action.async { implicit request ⇒
    Future.successful(Ok(uk.gov.hmrc.helptosavefrontend.views.html.register.create_account_help_to_save()))
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
  private def validateUser(userDetailsUri: Option[String])(implicit hc: HeaderCarrier): Result[Option[UserInfo]] = for {
    nino ← EitherT.fromOption[Future](retrieveNino(), "Unable to retrieve NINO")
    userInfo ← userInfoService.getUserInfo(userDetailsUri, nino)
    eligible ← eligibilityConnector.checkEligibility(nino)
  } yield eligible.fold(None, Some(userInfo))


  private def retrieveNino(): Option[String] = {
    Some("WM123456C")
  }

  def failedTwoFactor = Action.async { implicit request ⇒
    Future.successful(Ok(views.html.twofactor.you_need_two_factor("twoFactorURI")))
  }
}
