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
import uk.gov.hmrc.helptosavefrontend.connectors.EligibilityConnector
import uk.gov.hmrc.helptosavefrontend.views
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.frontend.auth.connectors.domain.Accounts

import scala.concurrent.Future

@Singleton
class HelpToSave @Inject()(val messagesApi: MessagesApi,
                           eligibilityConnector: EligibilityConnector) extends HelpToSaveController with I18nSupport  {


  val notEligible = Action.async { implicit request ⇒
    Future.successful(Ok(uk.gov.hmrc.helptosavefrontend.views.html.core.not_eligible()))
  }

  def start = Action.async { implicit request ⇒
    Future.successful(Ok(uk.gov.hmrc.helptosavefrontend.views.html.core.start()))
  }

  def declaration  =
    authorisedHtsUser { implicit authContext => implicit request ⇒
      retrieveNino(authContext) match {
        case Some(nino) => eligibilityConnector.checkEligibility(nino)
          .map(result ⇒
            Ok(result.fold(
              views.html.core.not_eligible(),
              user ⇒ uk.gov.hmrc.helptosavefrontend.views.html.register.declaration(user)
            )))
        case None => Future.successful(Ok(views.html.core.not_eligible()))
      }
  }
  def retrieveNino(authContext: AuthContext): Option[String] = {
    def getNino(accounts:Accounts):Option[String] = (accounts.paye,accounts.tai,accounts.tcs,accounts.iht) match {
      case (Some(paye),_,_,_) => Some(paye.nino.nino)
      case (_,Some(tai),_,_) => Some(tai.nino.nino)
      case (_,_,Some(tcs),_) => Some(tcs.nino.nino)
      case (_,_,_,Some(iht)) => Some(iht.nino.nino)
      case _ =>  None
    }
    //todo figure out what do to do if nino is not returned via signin
    getNino(authContext.principal.accounts)
  }

  def identityCheckFailed = Action.async { implicit request ⇒
    Future.successful(Ok(views.html.exceptions.identityCheckFailed()))
  }

}
