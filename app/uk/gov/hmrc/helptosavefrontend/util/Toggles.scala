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

import play.api.{Configuration, Logger}
import java.time.Instant

object Toggles {
  case class FEATURE[A](name: String, conf: Configuration, unconfiguredVal: Option[A], logger: Logger) {
    def enabled(): FEATURE_THEN[A] = {
      conf.getBoolean(s"feature-toggles.$name.enabled") match {
        case Some(b) =>
          logger.info("FEATURE: " + name + (if (b) " enabled" else " disabled"))
          FEATURE_THEN(name, b, unconfiguredVal, logger)
        case None => throw new RuntimeException(s"FEATURE($name) is not present in configuration file - misconfigured")
      }
    }
  }
  object FEATURE {
    def apply[A](name: String, conf: Configuration): FEATURE[A] = FEATURE[A](name: String, conf: Configuration, None, Logger(name))
    def apply[A](name: String, conf: Configuration, unconfiguredVal: A): FEATURE[A] = FEATURE[A](name: String, conf: Configuration, Some(unconfiguredVal), Logger(name))
  }


  case class FEATURE_THEN[A](name: String, enabled: Boolean, unconfiguredVal: Option[A], logger: Logger) {
    def thenDo(action: => A): Either[Option[A], A] = {
      if (enabled) {
        val startTime = Instant.now.toEpochMilli
        val result = action
        val endTime = Instant.now.toEpochMilli
        logger.info("FEATURE: " + name + " executed in " + (endTime - startTime).toString + " milliseconds.")
        Right(result)
      } else {
        Left(unconfiguredVal)
      }
    }
  }
  object FEATURE_THEN

  implicit def eitherPop[A](e: Either[Option[A], A]): A = {
    e match {
      case Right(a) => a
      case Left(Some(a)) => a
      case Left(None) => throw new RuntimeException(s"FEATURE has no otherwise branch and no default value")
    }
  }

  implicit class EitherExtend[A](e: Either[A, A]) {
    def otherwise(action: => A) = e match {
      case Right(a) => a
      case Left(a) => action
    }
  }
}
