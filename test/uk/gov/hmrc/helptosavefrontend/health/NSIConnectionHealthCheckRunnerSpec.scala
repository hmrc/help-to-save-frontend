/*
 * Copyright 2018 HM Revenue & Customs
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
import uk.gov.hmrc.helptosavefrontend.health.HealthCheck.PerformHealthCheck
import uk.gov.hmrc.helptosavefrontend.health.NSIConnectionHealthCheck.NSIConnectionHealthCheckRunner
import uk.gov.hmrc.helptosavefrontend.health.NSIConnectionHealthCheck.NSIConnectionHealthCheckRunner.Payload
import uk.gov.hmrc.helptosavefrontend.health.NSIConnectionHealthCheck.NSIConnectionHealthCheckRunner.Payload.{Payload1, Payload2}
import uk.gov.hmrc.helptosavefrontend.models.userinfo.NSIUserInfo
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class NSIConnectionHealthCheckRunnerSpec extends ActorTestSupport("NSIConnectionHealthCheckRunnerSpec") {

  val nsiConnector: NSIConnector = mock[NSIConnector]

  val payload: Payload = Payload.Payload1

  def newRunner(payload: Payload): ActorRef = system.actorOf(Props(new NSIConnectionHealthCheckRunner(
    nsiConnector,
    mockMetrics,
    payload,
    ninoLoggingEnabled = true
  )))

  def mockNSIConnectorTest(expectedPayload: Payload)(result: Option[Either[String, Unit]]): Unit =
    (nsiConnector.healthCheck(_: NSIUserInfo)(_: HeaderCarrier, _: ExecutionContext))
      .expects(expectedPayload.value, *, *)
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
          mockNSIConnectorTest(Payload1)(Some(Right(())))

          val runner = newRunner(Payload1)
          runner ! PerformHealthCheck

          expectMsgType[HealthCheckResult.Success]
        }

      "call the NSIConnector to do the test and reply back with a negative result " +
        "if the NSIConnector returns a failure" in {
            def test(mockActions: ⇒ Unit): Unit = {
              mockActions

              val runner = newRunner(Payload2)
              runner ! PerformHealthCheck
              expectMsgType[HealthCheckResult.Failure]
            }

          test(mockNSIConnectorTest(Payload2)(Some(Left(""))))
          test(mockNSIConnectorTest(Payload2)(None))
        }
    }
  }

}
