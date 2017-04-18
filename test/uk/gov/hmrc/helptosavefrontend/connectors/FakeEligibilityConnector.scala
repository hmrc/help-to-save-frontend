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

package uk.gov.hmrc.helptosavefrontend.connectors

import akka.actor.ActorRef
import uk.gov.hmrc.helptosavefrontend.connectors.FakeEligibilityConnector.CheckEligibility
import uk.gov.hmrc.helptosavefrontend.models.{EligibilityResult, UserDetails}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{Future, Promise}

class FakeEligibilityConnector(reportTo: ActorRef) extends EligibilityConnector {

  override def checkEligibility(nino: String)(implicit hc: HeaderCarrier): Future[EligibilityResult] = {
    val promise = Promise[EligibilityResult]()
    reportTo ! CheckEligibility(promise)
    promise.future
  }

}

object FakeEligibilityConnector {

  case class CheckEligibility(promise: Promise[EligibilityResult])

}
