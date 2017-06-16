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
  * @param extraParams Extra parameters read from the config file. Use `addA` or `addAn` to accumulate
  *                    parameters
  * @param log This type signature is used to facilitate testing of logging. (mocking frameworks do not
  *            currently seem to support by-name parameters very well)
  * @param t Enables conversion of internal HList to a tuple
  * @tparam L The HList representing the list of extra parameters
  * @tparam T The tuple corresponding to the list of extra parameters
  */
case class FEATURE[L <: HList,T] private(name: String,
                                         config: Config,
                                         enabled: Boolean,
                                         private[util] val extraParams: L,
                                         log:  (LogLevel,String,Option[Throwable]) ⇒ Unit)(implicit t: Tupler.Aux[L,T]) {

  import LogLevel._

  private def log(level: LogLevel, message: String): Unit = log(level, message, None)

  private def log(level: LogLevel, message: String, error: Throwable): Unit = log(level, message, Some(error))

  private def time(): Long = System.nanoTime()

  private def withInternal[A] =  new {
    // convert the name into something of type A by reading it from the config. Return a new FEATURE with
    // the new parameter appended to the extraParams list. Use an anonymous class here to be able to
    // 'curry' the types (see http://caryrobbins.com/dev/scala-type-curry/) - we want to specify the type
    // A, but we want L2 and T2 below to be calculated
    def apply[L2 <: HList, T2](name: String)(
      implicit
      configs: Configs[A],
      p: Prepend.Aux[L, A :: HNil, L2],
      t: Tupler.Aux[L2,T2]
    ): FEATURE[L2,T2] = {
      val param = config.get[A](name).value
      copy(extraParams = extraParams ::: (param :: HNil))
    }
  }

  def withA[A] = withInternal[A]

  def withAn[A] = withInternal[A]

  def thenOrElse[A](ifTrue: T ⇒ A)(ifFalse: T ⇒ A): A = {
    val start = time()
    val result = if (enabled) {
      ifTrue(extraParams.tupled)
    } else {
      ifFalse(extraParams.tupled)
    }
    val end = time()
    log(INFO, s"Feature $name (enabled: $enabled) executed in ${end-start} ns")
    result
  }

}

object FEATURE {

  // converts a HList with a single element to the element itself rather than
  // a Tuple1 containing the element
  implicit def hlistTupler1[A]: Tupler.Aux[A::HNil, A] = new Tupler[A::HNil] {
    type Out = A
    def apply(l : A::HNil): Out = l match { case a::HNil => a }
  }

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
  private[util] def apply  (name: String, configuration: Configuration, log:  (LogLevel,String,Option[Throwable]) ⇒ Unit): FEATURE[HNil,Unit] = {
    val config = getConfig(name, configuration)
    FEATURE(name, config, config.getBoolean("enabled"), HNil, log)
  }

  /**
    * @param name Name of the feature. Configuration of the feature will be looked for in the path
    *             'feature-toggles.$name'
    * @param configuration The global configuration
    * @param log The logger to be used by the feature
    */
  def apply  (name: String, configuration: Configuration, log: Logger): FEATURE[HNil,Unit] = {
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
    FEATURE(name, config, config.getBoolean("enabled"), HNil, logFunction(log))
  }

}
