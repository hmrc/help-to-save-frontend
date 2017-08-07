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

package src.test.scala.hts.utils

class Configuration(val url: String, val BROWSER: String = System.getProperty("browser", "firefox"))

object Environment extends Enumeration {
  type Name = Value
  val Local, Dev, Qa, Staging = Value
}

object Configuration {

  lazy val host: String = Configuration.settings.url
  lazy val authHost: String = "http://localhost:9949"

  lazy val environment: Environment.Name = {
    val environmentProperty = Option(System.getProperty("environment")).getOrElse("local").toLowerCase
    environmentProperty match {
      case "local" => Environment.Local
      case "qa" => Environment.Qa
      case "dev" => Environment.Dev
      case "staging" => Environment.Staging
      case _ => throw new IllegalArgumentException(s"Environment '$environmentProperty' not known")
    }
  }

  lazy val settings: Configuration = create()

  private def create(): Configuration = {
    environment match {
      case Environment.Local =>
        new Configuration(
          url = "http://localhost:7000"
        )
      case Environment.Dev =>
        new Configuration(
          url = "https://www-dev.tax.service.gov.uk"
        )
      case Environment.Qa =>
        new Configuration(
          url = "https://www-qa.tax.service.gov.uk"
        )
      case Environment.Staging =>
        new Configuration(
          url = "https://www-staging.tax.service.gov.uk"
        )
      case _ => throw new IllegalArgumentException(s"Environment '$environment' not known")
    }
  }
}
