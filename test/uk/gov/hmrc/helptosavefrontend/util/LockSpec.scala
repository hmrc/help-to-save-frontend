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

package uk.gov.hmrc.helptosavefrontend.util

import akka.actor.{ActorRef, PoisonPill, Props}
import com.miguno.akka.testing.VirtualTime
import uk.gov.hmrc.helptosavefrontend.health.ActorTestSupport
import uk.gov.hmrc.helptosavefrontend.util.LockSpec.State
import uk.gov.hmrc.helptosavefrontend.util.lock.{Lock, LockProvider}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class LockSpec extends ActorTestSupport("LockSpec") {

  val testLockID = "lockID"

  val lockDuration: FiniteDuration = 1.hour

  val time: VirtualTime = new VirtualTime

  trait TestableExclusiveTimePeriodLock extends LockProvider {

    override val lockId: String = testLockID

    override val holdLockFor = lockDuration

  }

  val internalLock = mock[TestableExclusiveTimePeriodLock]

  def sendToSelf[A](a: A): A = {
    self ! a
    a
  }

  // create a lock where the Int in the State increases by 1
  // each time the lock is acquired and decreases by 1 each time
  // the lock is released
  def newLock(): ActorRef = system.actorOf(Props(
    new Lock[State](
      internalLock,
      time.scheduler,
      State(0),
      s ⇒ sendToSelf(State(s.i + 1)),
      s ⇒ sendToSelf(State(s.i - 1))
    )
  ))

  lazy val lock = newLock()

  def mockTryToAcquireOrRenewLock(result: Either[String, Option[Unit]]): Unit =
    (internalLock.tryToAcquireOrRenewLock(_: Future[Unit])(_: ExecutionContext))
      .expects(*, *)
      .returning(result.fold(e ⇒ Future.failed(new Exception(e)), Future.successful))

  def mockReleaseLock(result: Either[String, Unit]) =
    (internalLock.releaseLock: () ⇒ Future[Unit])
      .expects()
      .returning(result.fold(e ⇒ Future.failed(new Exception(e)), Future.successful))

  "The Lock" must {

    "try to acquire the lock when started and change the state if " +
      "it is successful" in {
        mockTryToAcquireOrRenewLock(Right(Some(())))

        awaitActorReady(lock)
        // even though the message is scheduled without delay we still
        // need to make the clock tick in order to get the scheduled task
        // to run
        time.advance(1L)
        expectMsg(State(1))
      }

    "not try to renew the lock while the lock is still active" in {
      time.advance((lockDuration - 2.milli).toMillis)
      expectNoMsg()
    }

    "try to renew the lock when the lock expires amd change the state if " +
      "it is unsuccessful" in {
        mockTryToAcquireOrRenewLock(Right(None))

        time.advance(1)
        expectMsg(State(0))
      }

    "not change the state if there is an error while trying to acquire the lock" in {
      mockTryToAcquireOrRenewLock(Left(""))

      time.advance(lockDuration.toMillis)
      expectNoMsg()
    }

    "release the lock after shutting down and change the state if the " +
      "release is successful" in {
        mockReleaseLock(Right(()))
        lock ! PoisonPill
        expectMsg(State(-1))
      }

    "release the lock after shutting down and not change the state if the " +
      "release is unsuccessful" in {
        inSequence{
          mockTryToAcquireOrRenewLock(Right(Some(())))
          mockReleaseLock(Left(""))
        }

        // start the actor
        val lock = newLock()
        awaitActorReady(lock)
        time.advance(1L)
        expectMsg(State(1))

        // now kill it
        lock ! PoisonPill
        expectNoMsg()
      }
  }

}

object LockSpec {

  case class State(i: Int)

}
