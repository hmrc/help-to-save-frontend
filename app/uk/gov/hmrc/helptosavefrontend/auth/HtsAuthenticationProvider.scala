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

import play.api.mvc.Request
import uk.gov.hmrc.play.frontend.auth.{AnyAuthenticationProvider, GovernmentGateway, Verify}

import scala.concurrent.Future

object HtsAuthenticationProvider extends AnyAuthenticationProvider {

  override def ggwAuthenticationProvider: GovernmentGateway = HtsGovernmentGateway

  override def verifyAuthenticationProvider: Verify = HtsIVerify

  override def login: String = throw new RuntimeException("Unused")

  //TODO: Implement this
  //  override def handleSessionTimeout(implicit request: Request[_]): Future[Result] = {
  //     Future.successful(Redirect(routes.ServiceController.sessionTimeOut().url))
  //  }

  override def redirectToLogin(implicit request: Request[_]): Future[FailureResult] = HtsGovernmentGateway.ggRedirect
}
