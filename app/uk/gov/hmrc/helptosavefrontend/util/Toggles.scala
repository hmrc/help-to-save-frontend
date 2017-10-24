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
import play.api.{Configuration, Logger}
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics.nanosToPrettyString
import uk.gov.hmrc.helptosavefrontend.util.Logging._

object Toggles {

  /**
   *
   * @param name Name of the feature. Configuration of the feature will be looked for in the path
   *             'feature-toggles.$name'
   * @param enabled Whether or not the feature should be enabled
   * @param logger The logger to use in logging
   */
  case class FEATURE private (name:    String,
                              enabled: Boolean,
                              logger:  Logger,
                              nino:    Option[NINO]) {

    @inline private def time(): Long = System.nanoTime()

    def thenOrElse[A](ifEnabled: ⇒ A, ifDisabled: ⇒ A): A = {
      val start = time()
      val result = if (enabled) ifEnabled else ifDisabled
      val end = time()
      nino.fold(
        logger.info(s"Feature $name (enabled: $enabled) executed in ${nanosToPrettyString(end - start)}")
      )(n ⇒
          logger.info(s"Feature $name (enabled: $enabled) executed in ${nanosToPrettyString(end - start)}", n)
        )
      result
    }

  }

  object FEATURE {

    private def getConfig(name: String, configuration: Configuration): Config =
      configuration.underlying.getConfig(s"feature-toggles.$name")

    /**
     * @param name Name of the feature. Configuration of the feature will be looked for in the path
     *             'feature-toggles.$name'
     * @param configuration The global configuration
     * @param logger The logger to be used by the feature
     */
    def apply(name: String, configuration: Configuration, logger: Logger, nino: Option[NINO] = None): FEATURE = {
      val config = getConfig(name, configuration)
      FEATURE(name, config.getBoolean("enabled"), logger, nino)
    }

  }

}
