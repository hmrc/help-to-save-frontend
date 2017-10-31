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

package uk.gov.hmrc.helptosavefrontend.util.lock

import akka.actor.{Actor, Cancellable, Props, Scheduler}
import akka.pattern.pipe
import org.joda.time
import reactivemongo.api.DB
import uk.gov.hmrc.helptosavefrontend.util.Logging
import uk.gov.hmrc.lock.{ExclusiveTimePeriodLock, LockMongoRepository, LockRepository}
import uk.gov.hmrc.helptosavefrontend.util.lock.LockProvider.ExclusiveTimePeriodLockProvider
import uk.gov.hmrc.helptosavefrontend.util.toFuture

import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

/**
 * This is an `Actor` which handles changing of state when a lock is acquired or a lock
 * is released. On start-up the `Lock` will try to acquire a lock using the given `LockProvider`.
 * If successful `onLockAcquired` is called on the `initialState`. Otherwise `onLockReleased` is
 * called on the `initialState`. The state is updated at this point.  When the lock duration has
 * passed this `Actor` will try to acquire/renew a lock again. If successful `onLockAcquired` is
 * called on the current state. Otherwise `onLockReleased` is called on the current state. This
 * continues until the `Actor` dies. Before death, this `Actor` will try to release the lock. If
 * successful `onLockReleased` is called on the current state.
 *
 * N.B.: If the process of trying to acquire/renew a lock fails for any reason the state is not changed.
 */
class Lock[State](lock:           LockProvider,
                  scheduler:      Scheduler,
                  initialState:   State,
                  onLockAcquired: State ⇒ State,
                  onLockReleased: State ⇒ State
) extends Actor with Logging {

  import Lock.LockMessages._

  import context.dispatcher

  var state: State = initialState

  var schedulerTask: Option[Cancellable] = None

  override def receive: Receive = {
    case AcquireLock ⇒
      val result = lock.tryToAcquireOrRenewLock[Unit](toFuture(()))
        .map(result ⇒ AcquireLockResult(result.isDefined))
        .recover{ case NonFatal(e) ⇒ AcquireLockFailure(e) }

      result pipeTo self

    case AcquireLockFailure(error) ⇒
      logger.warn(s"Could not acquire or renew lock: ${error.getMessage}. Leaving state as is")

    case AcquireLockResult(lockAcquired) ⇒
      if (lockAcquired) {
        logger.info(s"Lock successfully acquired (lockID: ${lock.lockId}")
        state = onLockAcquired(state)
      } else {
        logger.info(s"Unable to acquire lock (lockID: ${lock.lockId}")
        state = onLockReleased(state)
      }

  }

  override def preStart(): Unit = {
    super.preStart()
    schedulerTask = Some(scheduler.schedule(Duration.Zero, lock.holdLockFor, self, AcquireLock))
  }

  override def postStop(): Unit = {
    lock.releaseLock(lock.lockId, lock.serverId).onComplete{
      case Success(_) ⇒
        state = onLockReleased(state)

      case Failure(e) ⇒
        logger.warn(s"Could not release lock while shutting down: ${e.getMessage}")

    }

    super.postStop()
  }

}

object Lock {

  /**
    * These props uses the `ExclusiveTimePeriodLock` to implement the
    * `LockProvider` behaviour required by the `Lock` actor
    */
  def props[State](mongoDb:        () ⇒ DB,
                   lockID:         String,
                   lockDuration:   FiniteDuration,
                   scheduler:      Scheduler,
                   initialState:   State,
                   onLockAcquired: State ⇒ State,
                   onLockReleased: State ⇒ State): Props = {

    val lock: ExclusiveTimePeriodLock = new ExclusiveTimePeriodLock {
      override val holdLockFor: time.Duration = org.joda.time.Duration.millis(lockDuration.toMillis)

      override val repo: LockRepository = LockMongoRepository(mongoDb)

      override val lockId: String = lockID
    }

    Props(new Lock(ExclusiveTimePeriodLockProvider(lock), scheduler, initialState, onLockAcquired, onLockReleased))
  }

  private sealed trait LockMessages

  private object LockMessages {
    case object AcquireLock extends LockMessages
    case class AcquireLockResult(lockAcquired: Boolean) extends LockMessages
    case class AcquireLockFailure(error: Throwable) extends LockMessages

  }

}

