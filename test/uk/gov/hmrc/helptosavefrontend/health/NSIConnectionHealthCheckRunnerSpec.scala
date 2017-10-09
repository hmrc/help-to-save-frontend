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

import akka.actor.{ActorRef, Props}
import cats.data.EitherT
import uk.gov.hmrc.helptosavefrontend.connectors.NSIConnector
import uk.gov.hmrc.helptosavefrontend.health.HealthCheck.PerformTest
import uk.gov.hmrc.helptosavefrontend.health.NSIConnectionHealthTest.NSIConnectionHealthCheckRunner
import uk.gov.hmrc.helptosavefrontend.health.NSIConnectionHealthTest.NSIConnectionHealthCheckRunner.Payload
import uk.gov.hmrc.helptosavefrontend.models.NSIUserInfo
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class NSIConnectionHealthCheckRunnerSpec extends ActorTestSupport("NSIConnectionHealthCheckRunnerSpec") {

  val nsiConnector: NSIConnector = mock[NSIConnector]

  val payload: Payload = Payload.Payload1

  val runner: ActorRef = system.actorOf(Props(new NSIConnectionHealthCheckRunner(
    nsiConnector,
    mockMetrics,
    payload
  )))

  def mockNSIConnectorTest(result: Option[Either[String, Unit]]): Unit =
    (nsiConnector.test(_: NSIUserInfo)(_: HeaderCarrier, _: ExecutionContext))
      .expects(payload.value, *, *)
      .returning(
        EitherT(
          result.fold[Future[Either[String, Unit]]](
            Future.failed(new Exception(""))
          )(Future.successful))
      )

  "The NSIConnectionHealthCheckRunner" when {

    "sent a PerformTest message" must {

      "call the NSIConnector to do the test and reply back with a successful result " +
        "if the NSIConnector returns a success" in {
          mockNSIConnectorTest(Some(Right(())))
          runner ! PerformTest

          expectMsgType[HealthCheckResult.Success]
        }

      "call the NSIConnector to do the test and reply back with a negative result " +
        "if the NSIConnector returns a failure" in {
            def test(mockActions: ⇒ Unit): Unit = {
              mockActions

              runner ! PerformTest
              expectMsgType[HealthCheckResult.Failure]
            }

          test(mockNSIConnectorTest(Some(Left(""))))
          test(mockNSIConnectorTest(None))
        }
    }
  }

}
