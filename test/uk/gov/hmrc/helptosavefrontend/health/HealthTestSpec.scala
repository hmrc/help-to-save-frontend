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

import akka.actor.{Actor, ActorIdentity, ActorRef, Identify, Props}
import akka.pattern.{ask, pipe}
import akka.testkit.TestProbe
import akka.util.Timeout
import cats.data.EitherT
import com.codahale.metrics._
import com.kenshoo.play.metrics.Metrics
import com.miguno.akka.testing.VirtualTime
import com.typesafe.config.ConfigFactory
import uk.gov.hmrc.helptosavefrontend.connectors.NSIConnector
import uk.gov.hmrc.helptosavefrontend.health.HealthCheck.PerformHealthCheck
import uk.gov.hmrc.helptosavefrontend.health.HealthTestSpec.PagerDutyMessage.{PagerDutyAlert, PagerDutyResolved}
import uk.gov.hmrc.helptosavefrontend.health.HealthTestSpec.ProxyActor.Created
import uk.gov.hmrc.helptosavefrontend.health.HealthTestSpec.ProxyActor
import uk.gov.hmrc.helptosavefrontend.health.HealthTestSpec.TestNSIConnector.{GetTestResult, GetTestResultResponse}
import uk.gov.hmrc.helptosavefrontend.models.userinfo.NSIUserInfo
import uk.gov.hmrc.helptosavefrontend.util.Result
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

class HealthTestSpec extends ActorTestSupport("HealthTestSpec") {

  val testName = "test"

  val (runnerName1, runnerName2) = "runner1" → "runner2"

  val minimumTimeBetweenTests: FiniteDuration = 1.minute

  val timeBetweenTests: FiniteDuration = 1.minute

  val testTimeout: FiniteDuration = 30.seconds

  val numberOfTestsBetweenUpdates: Int = 10

  val maximumConsecutiveFailures: Int = 10

  val numberOfTestsBetweenAlerts: Int = 10

  val pagerDutyListener: TestProbe = TestProbe()

  val runnerListener: TestProbe = TestProbe()

  val metricsListener: TestProbe = TestProbe()

  val metrics: Metrics = new Metrics {

    override def defaultRegistry: MetricRegistry = new MetricRegistry {
      override def histogram(name: String): Histogram = new Histogram(new UniformReservoir()) {

        override def update(value: Int): Unit = {
          metricsListener.ref ! value
          super.update(value)
        }
      }
    }

    override def toJson: String = sys.error("Not used")
  }

  def newHealthCheck(maximumConsecutiveFailures: Int            = maximumConsecutiveFailures,
                     minimumTimeBetweenTests:    FiniteDuration = minimumTimeBetweenTests): (ActorRef, VirtualTime) = {
    val time = new VirtualTime
    val ref = system.actorOf(HealthCheck.props(
      testName,
      ConfigFactory.parseString(
        s"""
           |health.$testName {
           |  poll-period = ${timeBetweenTests.toString}
           |  minimum-poll-period = ${minimumTimeBetweenTests.toString}
           |  poll-timeout = ${testTimeout.toString}
           |  poll-count-between-updates = $numberOfTestsBetweenUpdates
           |  poll-count-failures-to-alert = $maximumConsecutiveFailures
           |  poll-count-between-pager-duty-alerts = $numberOfTestsBetweenAlerts
           |}
      """.stripMargin
      ),
      time.scheduler,
      metrics,
      () ⇒ pagerDutyListener.ref ! PagerDutyAlert,
      () ⇒ pagerDutyListener.ref ! PagerDutyResolved,
      Props(new ProxyActor(runnerListener.ref, runnerName1)),
      Props(new ProxyActor(runnerListener.ref, runnerName2))
    ))

    ref → time
  }

  val (healthCheck: ActorRef, time: VirtualTime) = newHealthCheck()

  def mockTest(runnerName: String, result: Either[String, Unit]): Unit = {
    val created = runnerListener.expectMsgType[Created]
    created.name shouldBe runnerName

    // watch child here to check that it dies later on
    runnerListener.watch(created.ref)

    runnerListener.expectMsg(PerformHealthCheck)
    runnerListener.reply(
      result.fold[HealthCheckResult](e ⇒ HealthCheckResult.Failure(e, 0L), _ ⇒ HealthCheckResult.Success("", 0L))
    )

    runnerListener.expectTerminated(created.ref)
  }

  "The HealthCheck" when {

    "it is in the OK state and" when {

      "the configured amount of time between tests has passed" must {

        var childRef: Option[ActorRef] = None

        "create a child runner from the given props" in {
          time.advance(timeBetweenTests - 1.millisecond)
          expectNoMsg()

          // now move time forward to when the timer is triggered
          time.advance(1.millisecond)
          val created = runnerListener.expectMsgType[Created]
          created.name shouldBe runnerName1
          childRef = Some(created.ref)

          runnerListener.watch(created.ref)
        }

        "ask it to perform a test" in {
          runnerListener.expectMsg(PerformHealthCheck)
        }

        "kill the child once it has replied" in {
          runnerListener.reply(HealthCheckResult.Success("", 0))
          runnerListener.expectTerminated(childRef.getOrElse(sys.error("Could not find child actor ref")))
        }

        "record the number of failures as 0 in the metrics" in {
          metricsListener.expectMsg(0)
        }

        "create a child runner with the next set of props when " +
          "the configured number of successful tests between updates have " +
          "been performed" in {
            (2 until numberOfTestsBetweenUpdates).foreach { _ ⇒
              time.advance(timeBetweenTests)
              mockTest(runnerName1, Right(()))
              metricsListener.expectMsg(0)
            }

            time.advance(timeBetweenTests)
            mockTest(runnerName2, Right(()))
            metricsListener.expectMsg(0)
          }

        "create a child runner with the first set of props when " +
          "the configured number of successful tests between updates have " +
          "been performed and all the props given have been used" in {
            (1 until numberOfTestsBetweenUpdates).foreach { _ ⇒
              time.advance(timeBetweenTests)
              mockTest(runnerName2, Right(()))
              metricsListener.expectMsg(0)
            }

            time.advance(timeBetweenTests)
            mockTest(runnerName1, Right(()))
            metricsListener.expectMsg(0)
          }

        "fall back to the minimum time between tests if the configured time between tests falls " +
          "below the configured minimum threshold" in {
            val minimumTimeBetweenTests = timeBetweenTests + 1.second
            val (healthCheck, time) = newHealthCheck(minimumTimeBetweenTests = minimumTimeBetweenTests)

            awaitActorReady(healthCheck)

            time.advance(timeBetweenTests)
            runnerListener.expectNoMsg()

            time.advance(1.second)
            mockTest(runnerName1, Left(""))

            metricsListener.expectMsg(1)

          }

      }

      "a test fails" must {

        "record the number of failures as 1" in {
          time.advance(timeBetweenTests)
          mockTest(runnerName1, Left(""))
          metricsListener.expectMsg(1)
        }

        "record the number of failures as 1 and alert pager duty if the " +
          "maximum number of consecutive failures is 1" in {
            val (healthCheck, time) = newHealthCheck(1)
            awaitActorReady(healthCheck)

            time.advance(timeBetweenTests)
            mockTest(runnerName1, Left(""))

            pagerDutyListener.expectMsg(PagerDutyAlert)
            metricsListener.expectMsg(1)
          }
      }

      "a test times out" must {

        "record the number of failures as 1" in {
          val (healthCheck, time) = newHealthCheck(maximumConsecutiveFailures)
          awaitActorReady(healthCheck)

          time.advance(timeBetweenTests)

          val created = runnerListener.expectMsgType[Created]
          created.name shouldBe runnerName1
          runnerListener.watch(created.ref)

          runnerListener.expectMsg(PerformHealthCheck)
          // don't reply - advance time to the configured test timeout
          time.advance(testTimeout)

          runnerListener.expectTerminated(created.ref)
          metricsListener.expectMsg(1)
        }

      }
    }

    "it is in the failing state and " when {

      "a test passes" must {

        "record the number of failures as 0" in {
          time.advance(timeBetweenTests)
          mockTest(runnerName1, Right(()))
          metricsListener.expectMsg(0)
        }

      }

      "the configured number of maximum consecutive test failures has been reached" must {

        "record the appropriate number of failures and trigger pager duty" in {
          (1 until maximumConsecutiveFailures).foreach{ i ⇒
            time.advance(timeBetweenTests)
            mockTest(runnerName1, Left(""))
            metricsListener.expectMsg(i)
          }

          time.advance(timeBetweenTests)
          mockTest(runnerName1, Left(""))
          metricsListener.expectMsg(maximumConsecutiveFailures)
          pagerDutyListener.expectMsg(PagerDutyAlert)
        }

      }

    }

    "it is in the failed state and " when {

      "the configured number of test failures between pager duty alerts has been reached" must {

        "record the appropriate number of failures and trigger pager duty again" in {
          (1 until numberOfTestsBetweenAlerts).foreach{ i ⇒
            time.advance(timeBetweenTests)
            mockTest(runnerName1, Left(""))
            metricsListener.expectMsg(maximumConsecutiveFailures + i)
          }
          // pager duty shouldn't have been alerted at this point
          pagerDutyListener.expectNoMsg()

          time.advance(timeBetweenTests)
          mockTest(runnerName1, Left(""))
          metricsListener.expectMsg(maximumConsecutiveFailures + numberOfTestsBetweenAlerts)
          pagerDutyListener.expectMsg(PagerDutyAlert)
        }

      }

      "a test passes" must {

        "record the number of failures as 0 and resolve pager duty" in {
          time.advance(timeBetweenTests)
          mockTest(runnerName1, Right(()))
          metricsListener.expectMsg(0)
          pagerDutyListener.expectMsg(PagerDutyResolved)
        }

      }
    }
  }

}

object HealthTestSpec {

  sealed trait PagerDutyMessage

  object PagerDutyMessage {
    case object PagerDutyAlert extends PagerDutyMessage

    case object PagerDutyResolved extends PagerDutyMessage
  }

  class TestNSIConnector(reportTo: ActorRef) extends NSIConnector {
    implicit val timeout: Timeout = Timeout(3.seconds)

    override def createAccount(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[NSIConnector.SubmissionResult] =
      sys.error("Not used")

    override def updateEmail(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ex: ExecutionContext): Result[Unit] =
      sys.error("Not used")

    override def healthCheck(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ex: ExecutionContext): Result[Unit] = {
      val result: Future[Option[Either[String, Unit]]] = (reportTo ? GetTestResult(userInfo)).mapTo[GetTestResultResponse].map(_.result)
      EitherT(result.flatMap{
        _.fold[Future[Either[String, Unit]]](Future.failed(new Exception("")))(Future.successful)
      })
    }
  }

  object TestNSIConnector {
    case class GetTestResult(payload: NSIUserInfo)

    case class GetTestResultResponse(result: Option[Either[String, Unit]])
  }

  class ProxyActor(ref: ActorRef, name: String) extends Actor {
    import context.dispatcher

    implicit val timeout: Timeout = 3.seconds

    override def preStart(): Unit = {
      super.preStart()
      ref ! Created(self, name)
    }

    override def receive: Receive = {
      case PerformHealthCheck ⇒ (ref ? PerformHealthCheck) pipeTo sender()
    }

  }

  object ProxyActor {
    case class Created(ref: ActorRef, name: String)
  }

}
