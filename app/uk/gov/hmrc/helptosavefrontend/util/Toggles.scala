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

object Toggles {
  case class FEATURE[A](name: String, conf: Configuration, unconfiguredVal: A, logger: Logger = Logger("FEATURE")) {
    def enabled(): FEATURE_THEN[A] = {
      conf.getBoolean(s"toggles.$name.enabled") match {
        case Some(b) => FEATURE_THEN(name, b, unconfiguredVal)
        case None => throw new RuntimeException(s"FEATURE($name) is not present in configuration file - misconfigured")
      }
    }

    def enabledWith(additional: String): FEATURE_THEN_KEY[A] = ???
  }
  object FEATURE

  case class FEATURE_THEN[A](name: String, enabled: Boolean, unconfiguredVal: A) {
    def thenDo(action: => A): Either[A, A] = {
      if (enabled) {
        val result = action
        Right(result)
      } else {
        Left(unconfiguredVal)
      }
    }
  }
  object FEATURE_THEN

  case class FEATURE_THEN_KEY[A](name: String, key: String, enabled: Boolean, hasKey: Boolean, unconfiguredVal: A)
  object FEATURE_THEN_KEY

  implicit def eitherPop[A](e: Either[A, A]): A = {
    e match {
      case Right(a) => a
      case Left(a) => a
    }
  }

  implicit class EitherExtend[A](e: Either[A, A]) {
    def otherwise(action: => A) = e match {
      case Right(a) => a
      case Left(a) => action
    }

  }
}
