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

package uk.gov.hmrc.helptosavefrontend.auth

import play.api.Logger
import uk.gov.hmrc.helptosavefrontend.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.controllers.routes
import uk.gov.hmrc.play.frontend.auth._
import uk.gov.hmrc.play.frontend.auth.connectors.domain.Accounts

object HelpToSaveAuthentication extends  GovernmentGateway{
  override def continueURL: String = FrontendAppConfig.redirectUrlForAuth
  override def loginURL: String = FrontendAppConfig.companySignInloginUrl
}

case class HtsRegime(authenticationProvider: AuthenticationProvider) extends TaxRegime {

  //todo find out what to do here ????
  override def isAuthorised(accounts: Accounts): Boolean = true

  override def authenticationType: AuthenticationProvider = authenticationProvider

  override def unauthorisedLandingPage = {

    Some(routes.HelpToSave.notEligible().url)
  }
}
