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

package uk.gov.hmrc.helptosavefrontend.health

import akka.actor.{Actor, ActorRef, Props}
import akka.testkit.TestProbe
import uk.gov.hmrc.helptosavefrontend.health.HealthCheck.PerformHealthCheck

import scala.concurrent.Future

class HealthCheckRunnerSpec extends ActorTestSupport("HealthCheckRunnerSpec") {

  class TestRunner(ref: ActorRef, result: HealthCheckResult) extends Actor with HealthCheckRunner {

    def performTest(): Future[HealthCheckResult] = {
      ref ! "Hello!"
      Future.successful(result)
    }

  }

  "Actors extending the HealthCheckRunner trait" must {

    "execute the implemented performTest logic when sent a PerformTest message" in {
      val probe = TestProbe()
      val result = HealthCheckResult.Success(0L)
      val runner = system.actorOf(Props(new TestRunner(probe.ref, result)))

      runner ! PerformHealthCheck
      probe.expectMsg("Hello!")
      expectMsg(result)
    }

  }

}
