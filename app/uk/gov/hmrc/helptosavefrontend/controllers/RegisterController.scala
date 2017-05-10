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
import uk.gov.hmrc.helptosavefrontend.auth.HtsCompositePageVisibilityPredicate.twoFactorURI
import uk.gov.hmrc.helptosavefrontend.connectors._
import uk.gov.hmrc.helptosavefrontend.models.{HTSSession, NSIUserInfo, UserInfo}
import uk.gov.hmrc.helptosavefrontend.models.UserInfo
import uk.gov.hmrc.helptosavefrontend.services.userinfo.UserInfoService
import uk.gov.hmrc.helptosavefrontend.util.Result
import uk.gov.hmrc.helptosavefrontend.views
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.frontend.auth.connectors.domain.Accounts
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

@Singleton
class RegisterController @Inject()(val sessionCacheConnector: SessionCacheConnector,
                                   val messagesApi: MessagesApi,
                                   eligibilityConnector: EligibilityConnector,
                                   citizenDetailsConnector: CitizenDetailsConnector,
                                   nSAndIConnector: NSAndIConnector) extends HelpToSaveController with I18nSupport {

  val userInfoService = new UserInfoService(authConnector, citizenDetailsConnector)

  def declaration =
    AuthorisedHtsUserAction { implicit authContext ⇒
      implicit request ⇒
        validateUser(authContext).fold(
          error ⇒ {
            Logger.error(s"Could not perform eligibility check: $error")
            InternalServerError("")
          }, _.fold(
            Ok(views.html.core.not_eligible()))(
            userDetails ⇒ {
              sessionCacheConnector.put(HTSSession(Some(userDetails)))
              Ok(views.html.register.declaration(userDetails))
            }
          )
        )
    }

  def getCreateAccountHelpToSave = AuthorisedHtsUserAction { implicit authContext =>
    implicit request ⇒
      Future.successful(Ok(uk.gov.hmrc.helptosavefrontend.views.html.register.create_account_help_to_save()))
  }


  def postCreateAccountHelpToSave = AuthorisedHtsUserAction { implicit authContext =>
    implicit request ⇒
      sessionCacheConnector.get.flatMap({
        case Some(session) ⇒
          session.nSIUserInfo match {
            case Some(userInfo) ⇒
              NSIUserInfo(userInfo).fold(
                errors ⇒
                  Future.successful(Ok(uk.gov.hmrc.helptosavefrontend.views.html.core.stub_page(errors.toList.mkString(",")))),
                success ⇒
                  nSAndIConnector.createAccount(success).flatMap(_.fold
                  (errors ⇒ Future.successful(Ok(uk.gov.hmrc.helptosavefrontend.views.html.core.stub_page(errors.message))),
                    success ⇒ Future.successful(Ok(uk.gov.hmrc.helptosavefrontend.views.html.core.stub_page("This is a stub for nsi")))
                  ))
              )
            case None ⇒
              Future.successful(Ok(uk.gov.hmrc.helptosavefrontend.views.html.core.stub_page("No user Info :(")))
          }
        case None ⇒
          Future.successful(Ok(uk.gov.hmrc.helptosavefrontend.views.html.core.stub_page("No session :(")))
      }
      )
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
  private def validateUser(authContext: AuthContext)(implicit hc: HeaderCarrier): Result[Option[UserInfo]] = for {
    nino ← EitherT.fromOption[Future](retrieveNino(authContext), "Unable to retrieve NINO")
    userInfo ← userInfoService.getUserInfo(authContext, nino)
    eligible ← eligibilityConnector.checkEligibility(nino)
  } yield eligible.fold(None, Some(userInfo))


  private def retrieveNino(authContext: AuthContext): Option[String] = {
    def getNino(accounts: Accounts): Option[String] = (accounts.paye, accounts.tai, accounts.tcs, accounts.iht) match {
      case (Some(paye), _, _, _) => Some(paye.nino.nino)
      case (_, Some(tai), _, _) => Some(tai.nino.nino)
      case (_, _, Some(tcs), _) => Some(tcs.nino.nino)
      case (_, _, _, Some(iht)) => Some(iht.nino.nino)
      case _ => None
    }

    getNino(authContext.principal.accounts)
  }

  def failedTwoFactor = Action.async { implicit request ⇒
    Future.successful(Ok(views.html.twofactor.you_need_two_factor(twoFactorURI.toString)))
  }
}
