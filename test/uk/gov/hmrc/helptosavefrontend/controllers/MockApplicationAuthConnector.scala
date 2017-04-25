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

import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.auth.connectors.domain._
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet}

import scala.concurrent.Future

object MockApplicationAuthConnector extends AuthConnector {
  override val serviceUrl: String = ""
  override def http :HttpGet = ???
  val nino:Option[String]=Some("WM123456C")

  override def currentAuthority(implicit hc : HeaderCarrier) : Future[Option[Authority]] = {
    nino match {
      case Some(nino) => Future.successful(
        Some(
          Authority(uri = s"/path/to/authority",
            accounts = Accounts(paye = Some(PayeAccount(s"/taxcalc/$nino", Nino(nino))), tai = Some(TaxForIndividualsAccount(s"/tai/$nino", Nino(nino)))),
            loggedInAt = None,
            previouslyLoggedInAt = None,
            credentialStrength = CredentialStrength.Strong,
            confidenceLevel = ConfidenceLevel.L200,
            userDetailsLink = Some("/user-details/mockuser"),
            enrolments = Some("/auth/oid/mockuser/enrolments"),
            ids = Some("/auth/oid/mockuser/ids"),
            legacyOid = "mockuser")))

      case None => Future.successful(None)
    }
  }

}