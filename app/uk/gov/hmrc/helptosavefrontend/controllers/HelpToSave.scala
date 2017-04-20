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

import akka.event.slf4j.Logger
import com.google.inject.Inject
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.helptosavefrontend.connectors.{AuthConnector, EligibilityConnector}
import uk.gov.hmrc.helptosavefrontend.views
import uk.gov.hmrc.play.frontend.auth.connectors.domain.Accounts
import uk.gov.hmrc.play.frontend.auth.{AuthContext, Principal}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HelpToSave @Inject()(val messagesApi: MessagesApi,
                           eligibilityConnector: EligibilityConnector) extends HelpToSaveController with I18nSupport  {

  val nino = "A434387534D"

  val notEligible = Action.async { implicit request ⇒
    Future.successful(Ok(uk.gov.hmrc.helptosavefrontend.views.html.core.not_eligible()))
  }

  def start = Action.async { implicit request ⇒
    Future.successful(Ok(uk.gov.hmrc.helptosavefrontend.views.html.core.start()))
  }

  def declaration =
    authorisedHtsUser { implicit authContext => implicit request ⇒
       eligibilityConnector.checkEligibility(retrieveNino(authContext).nino)
        .map(result ⇒
          Ok(result.fold(
            views.html.core.not_eligible(),
            user ⇒ uk.gov.hmrc.helptosavefrontend.views.html.register.declaration(user)
          )))
  }
  def retrieveNino(authContext: AuthContext)(implicit hc: HeaderCarrier, ec: ExecutionContext): Nino = {
    def getNino(accounts:Accounts):Nino = (accounts.paye,accounts.tai,accounts.tcs,accounts.iht) match {
      case (Some(paye),_,_,_) => paye.nino
      case (_,Some(tai),_,_) => tai.nino
      case (_,_,Some(tcs),_) => tcs.nino
      case (_,_,_,Some(iht)) => iht.nino
      case _ =>   Nino("Hello world we dont have a nino")
    }
    getNino(authContext.principal.accounts)
  }

}
