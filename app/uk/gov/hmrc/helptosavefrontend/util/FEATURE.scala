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

import com.typesafe.config.Config
import configs.Configs
import configs.syntax._
import play.api.{Configuration, Logger}
import shapeless.ops.hlist.Prepend
import shapeless.{::, HList, HNil}
import shapeless.ops.hlist._
import uk.gov.hmrc.helptosavefrontend.util.FEATURE.LogLevel
import uk.gov.hmrc.helptosavefrontend.util.FEATURE.LogLevel._

/**
  *
  * @param name Name of the feature. Configuration of the feature will be looked for in the path
  *             'feature-toggles.$name'
  * @param config The global configuration
  * @param enabled Whether or not the feature should be enabled
  * @param log This type signature is used to facilitate testing of logging. (mocking frameworks do not
  *            currently seem to support by-name parameters very well)
  */
case class FEATURE private(name: String,
                           config: Config,
                           enabled: Boolean,
                           log:  (LogLevel, String, Option[Throwable]) ⇒ Unit) {

  import LogLevel._

  @inline private def log(level: LogLevel, message: String): Unit = log(level, message, None)

  @inline private def log(level: LogLevel, message: String, error: Throwable): Unit = log(level, message, Some(error))

  @inline private def time(): Long = System.nanoTime()

  def thenOrElse[A](ifTrue: ⇒ A, ifFalse: ⇒ A): A = {
    val start = time()
    val result = if (enabled) ifTrue else ifFalse
    val end = time()
    log(INFO, s"Feature $name (enabled: $enabled) executed in ${end-start} ns")
    result
  }

}

object FEATURE {

  private[util] sealed trait LogLevel

  private[util] object LogLevel {
    case object TRACE extends LogLevel
    case object DEBUG extends LogLevel
    case object INFO extends LogLevel
    case object WARN extends LogLevel
    case object ERROR extends LogLevel
  }

  private def getConfig(name: String, configuration: Configuration): Config =
    configuration.underlying.getConfig(s"feature-toggles.$name")

  // this apply is used for testing
  private[util] def apply(name: String, configuration: Configuration, log: (LogLevel, String, Option[Throwable]) ⇒ Unit): FEATURE = {
    val config = getConfig(name, configuration)
    FEATURE(name, config, config.getBoolean("enabled"), log)
  }

  /**
    * @param name Name of the feature. Configuration of the feature will be looked for in the path
    *             'feature-toggles.$name'
    * @param configuration The global configuration
    * @param log The logger to be used by the feature
    */
  def apply(name: String, configuration: Configuration, log: Logger): FEATURE = {
    def logFunction(l: Logger): ((LogLevel,String,Option[Throwable]) ⇒ Unit) = {
      case (level: LogLevel, message: String, error: Option[Throwable]) ⇒
        level match {
          case TRACE ⇒ error.fold(log.trace(message))(log.trace(message,_))
          case DEBUG ⇒ error.fold(log.debug(message))(log.debug(message,_))
          case INFO  ⇒ error.fold(log.info(message))(log.info(message,_))
          case WARN  ⇒ error.fold(log.warn(message))(log.warn(message,_))
          case ERROR ⇒ error.fold(log.error(message))(log.error(message,_))
        }
    }

    val config = getConfig(name, configuration)
    FEATURE(name, config, config.getBoolean("enabled"), logFunction(log))
  }

}
