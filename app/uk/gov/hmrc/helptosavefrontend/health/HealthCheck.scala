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

import akka.actor.{Actor, ActorLogging, Cancellable, PoisonPill, Props, Scheduler}
import akka.pattern.{after, ask, pipe}
import cats.data.{NonEmptyVector, OptionT}
import cats.instances.future._
import cats.instances.int._
import cats.syntax.eq._
import com.codahale.metrics.Histogram
import com.kenshoo.play.metrics.Metrics
import configs.syntax._
import com.typesafe.config.Config
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics.nanosToPrettyString

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

/**
 * An actor which handles the results of a generic health test. The tests that will be run are
 * defined by `runnerProps`. The [[HealthCheck]] actor will repeatedly create a child using these props
 * at a configured time interval. These children will have to respond to a
 * [[uk.gov.hmrc.helptosavefrontend.health.HealthCheck.PerformTest]] message and respond with a [[HealthCheckResult]].
 * The appropriate interface of the children is handily provided by the [[HealthCheckRunner]] trait. After a
 * configured number of successful tests the [[HealthCheck]] actor will switch tests by using the next [[Props]] in
 * `runnerProps`. When the last [[Props]] in `runnerProps` is used, the next switch will result in the first
 * [[Props]] being used again and the [[Props]] are iterated through again in a cyclical fashion.
 *
 * The [[HealthCheck]] actor will handle [[HealthCheckResult]]s by keeping a record of the number of failures using
 * the given [[Metrics]] and triggering pager duty alerts when a configured number of consecutive failures
 * has occurred.
 *
 * @param name The name of the test
 * @param config The config variables will be looked for under the namespace `health-check.{name}` where `name` is
 *               the name of the test
 * @param scheduler      The scheduler used to schedule the health tests
 * @param metrics        The metrics to be updated
 * @param pagerDutyAlert A function which will alert pager duty
 * @param runnerProps    The props of the tests to be run
 */
class HealthCheck(name:             String,
                  config:           Config,
                  scheduler:        Scheduler,
                  metrics:          Metrics,
                  pagerDutyAlert:   () ⇒ Unit,
                  pagerDutyResolve: () ⇒ Unit,
                  runnerProps:      NonEmptyVector[Props])
  extends Actor with ActorLogging {

  import uk.gov.hmrc.helptosavefrontend.health.HealthCheck._

  import context.dispatcher

  val minimumTimeBetweenTests: FiniteDuration =
    config.get[FiniteDuration](s"health.$name.minimum-poll-period").value

  val timeBetweenTests: FiniteDuration =
    config.get[FiniteDuration](s"health.$name.poll-period").value.max(minimumTimeBetweenTests)

  val testTimeout: FiniteDuration =
    config.get[FiniteDuration](s"health.$name.poll-timeout").value

  val numberOfTestsBetweenUpdates: Int =
    config.get[Int](s"health.$name.poll-count-between-updates").value

  val maximumConsecutiveFailures: Int =
    config.get[Int](s"health.$name.poll-count-failures-to-alert").value

  val numberOfTestsBetweenAlerts: Int =
    config.get[Int](s"health.$name.poll-count-between-pager-duty-alerts").value

  val failureHistogram: Histogram = metrics.defaultRegistry.histogram(s"health.$name.number-of-failures")

  val propsQueue: NonEmptyCyclicalQueue[Props] = NonEmptyCyclicalQueue(runnerProps)

  var currentRunnerProps: Props = propsQueue.next()

  var performTestTask: Option[Cancellable] = None

  override def receive: Receive = ok(0)

  def ok(count: Int): Receive = performTest orElse {
    case HealthCheckResult.Success(nanos) ⇒
      log.info(s"Health check $name OK ${timeString(nanos)}")
      val newCount = count + 1

      if (newCount === numberOfTestsBetweenUpdates - 1) {
        currentRunnerProps = propsQueue.next()
      }

      becomeOK(newCount % numberOfTestsBetweenUpdates)

    case HealthCheckResult.Failure(message, nanos) ⇒
      log.warning(s"Health check $name has started to fail $message ${timeString(nanos)}")

      if (maximumConsecutiveFailures > 1) {
        becomeFailing()
      } else {
        pagerDutyAlert()
        becomeFailed()
      }

  }

  def failing(fails: Int): Receive = performTest orElse {
    case HealthCheckResult.Success(nanos) ⇒
      log.info(s"Health check $name was failing but now OK ${timeString(nanos)}")
      becomeOK()

    case HealthCheckResult.Failure(message, nanos) ⇒
      log.warning(s"Health check $name still failing: $message ${timeString(nanos)}")
      val newFails = fails + 1

      if (newFails < maximumConsecutiveFailures) {
        becomeFailing(newFails)
      } else {
        pagerDutyAlert()
        becomeFailed()
      }

  }

  def failed(fails: Int): Receive = performTest orElse {
    case HealthCheckResult.Success(nanos) ⇒
      log.info(s"Health check $name had failed but now OK ${timeString(nanos)}")
      pagerDutyResolve()
      becomeOK()

    case HealthCheckResult.Failure(message, nanos) ⇒
      log.warning(s"Health check $name still failing: $message ${timeString(nanos)}")
      val newFails = fails + 1
      becomeFailed(newFails)

      if (newFails % numberOfTestsBetweenAlerts === 0) {
        pagerDutyAlert()
      }
  }

  def performTest: Receive = {
    case PerformTest ⇒
      val runner = context.actorOf(currentRunnerProps)
      val result: Future[HealthCheckResult] =
        withTimeout(
          runner.ask(PerformTest)(testTimeout).mapTo[HealthCheckResult],
          testTimeout
        ).getOrElse(HealthCheckResult.Failure("Test timed out", testTimeout.toNanos))
          .recover{ case NonFatal(e) ⇒ HealthCheckResult.Failure(e.getMessage, 0L) }

      result.onComplete{ _ ⇒ runner ! PoisonPill }
      result pipeTo self
  }

  def becomeOK(count: Int = 1): Unit = {
    failureHistogram.update(0)
    context become ok(count)
  }

  def becomeFailing(fails: Int = 1): Unit = {
    failureHistogram.update(fails)
    context become failing(fails)
  }

  def becomeFailed(fails: Int = maximumConsecutiveFailures): Unit = {
    failureHistogram.update(fails)
    context become failed(fails)
  }

  def withTimeout[A](f: Future[A], timeout: FiniteDuration): OptionT[Future, A] =
    OptionT(Future.firstCompletedOf(Seq(
      f.map(Some(_)),
      after(timeout, scheduler)(Future.successful(None))
    )))

  def timeString(nanos: Long): String = s"(time: ${nanosToPrettyString(nanos)})"

  override def preStart(): Unit = {
    super.preStart()
    performTestTask = Some(scheduler.schedule(timeBetweenTests, timeBetweenTests, self, PerformTest))
  }

  override def postStop(): Unit = {
    super.postStop()
    performTestTask.foreach(_.cancel())
  }
}

object HealthCheck {

  def props(
      name:             String,
      config:           Config,
      scheduler:        Scheduler,
      metrics:          Metrics,
      pagerDutyAlert:   () ⇒ Unit,
      pagerDutyResolve: () ⇒ Unit,
      runnerProps:      Props,
      otherRunnerProps: Props*): Props =
    Props(
      new HealthCheck(
        name, config, scheduler, metrics, pagerDutyAlert, pagerDutyResolve, NonEmptyVector(runnerProps, otherRunnerProps.toVector))
    )

  case object PerformTest

  /**
   * Uses the `NonEmptyVector` to produce a queue which when dequeueing an element will
   * put the dequeued element back on the end of the queue. In this way, this queue is
   * never-ending because once the elements in the `NonEmptyVector` have been exhausted
   * the queue will dequeue the same elements again. For example:
   * {{{
   *   val queue = NonEmptyCyclicalQueue(NonEmptyVector("a", Vector("b", "c"))
   *   queue.next()  // = "a"
   *   queue.next()  // = "b"
   *   queue.next()  // = "c"
   *   queue.next()  // = "a"
   *   queue.next()  // = "b"
   *   queue.next()  // = "c"
   *   queue.next()  // = "a"
   *   ...
   * }}}
   */
  private[health] case class NonEmptyCyclicalQueue[A](xs: NonEmptyVector[A]) {

    private var current = dequeueCyclical(xs)

    private def dequeueCyclical(as: NonEmptyVector[A]): (A, NonEmptyVector[A]) = as match {
      case NonEmptyVector(h, Vector()) ⇒ h → NonEmptyVector(h, Vector())
      case NonEmptyVector(h, t +: ts)  ⇒ h → NonEmptyVector(t, ts :+ h)
    }

    def next(): A = {
      val nextValue = current._1
      current = dequeueCyclical(current._2)
      nextValue
    }

  }

}
