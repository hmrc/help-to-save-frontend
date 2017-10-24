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

import java.time.LocalDate

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import com.google.inject.Inject
import configs.syntax._
import play.api.Configuration
import uk.gov.hmrc.helptosavefrontend.connectors.NSIConnector
import uk.gov.hmrc.helptosavefrontend.health.NSIConnectionHealthCheck.NSIConnectionHealthCheckRunner
import uk.gov.hmrc.helptosavefrontend.health.NSIConnectionHealthCheck.NSIConnectionHealthCheckRunner.Payload
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.models.NSIUserInfo
import uk.gov.hmrc.helptosavefrontend.models.NSIUserInfo.ContactDetails
import uk.gov.hmrc.helptosavefrontend.util.{Email, Logging}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future
import scala.util.control.NonFatal

class NSIConnectionHealthCheck @Inject() (system: ActorSystem, configuration: Configuration, metrics: Metrics, nSIConnector: NSIConnector) extends Logging {

  val name: String = "nsi-connection"

  val enabled: Boolean = configuration.underlying.get[Boolean](s"health.$name.enabled").value

  lazy val healthCheck: ActorRef = system.actorOf(
    HealthCheck.props(
      name,
      configuration.underlying,
      system.scheduler,
      metrics.metrics,
      () ⇒ (),
      () ⇒ (),
      NSIConnectionHealthCheckRunner.props(nSIConnector, metrics, Payload.Payload1),
      NSIConnectionHealthCheckRunner.props(nSIConnector, metrics, Payload.Payload2)
    )
  )

  // start the health check only if it is enabled
  if (enabled) {
    logger.info(s"HealthCheck $name enabled")
    val _ = healthCheck
  } else {
    logger.info(s"HealthCheck $name not enabled")
  }

}

object NSIConnectionHealthCheck {

  class NSIConnectionHealthCheckRunner(nsiConnector: NSIConnector, metrics: Metrics, payload: Payload) extends Actor with HealthCheckRunner with Logging {

    import context.dispatcher

    implicit val hc: HeaderCarrier = HeaderCarrier()

    override def performTest(): Future[HealthCheckResult] = {
      val timer = metrics.healthCheckTimer.time()

      nsiConnector.healthCheck(payload.value).value
        .map { result ⇒
          val time = timer.stop()
          result.fold[HealthCheckResult](e ⇒ HealthCheckResult.Failure(e, time), _ ⇒ HealthCheckResult.Success(time))
        }
        .recover{
          case NonFatal(e) ⇒
            val time = timer.stop()
            HealthCheckResult.Failure(e.getMessage, time)
        }
    }

  }

  object NSIConnectionHealthCheckRunner {

    def props(nsiConnector: NSIConnector, metrics: Metrics, payload: Payload): Props =
      Props(new NSIConnectionHealthCheckRunner(nsiConnector, metrics, payload))

    sealed trait Payload {
      val value: NSIUserInfo
    }

    object Payload {

      private def payload(email: Email): NSIUserInfo = NSIUserInfo(
        "Service", "Account", LocalDate.ofEpochDay(0L), "XX999999X",
                              ContactDetails("Health", "Check", None, None, None, "AB12CD", None, email))

      private[health] case object Payload1 extends Payload {
        override val value: NSIUserInfo = payload("healthcheck_ping@noreply.com")
      }

      private[health] case object Payload2 extends Payload {
        override val value: NSIUserInfo = payload("healthcheck_pong@noreply.com")
      }

    }
  }

}
