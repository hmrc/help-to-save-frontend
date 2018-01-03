/*
 * Copyright 2018 HM Revenue & Customs
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

package hts.utils

sealed trait Environment

object Environment {

  case object Local extends Environment
  case object Dev extends Environment
  case object Qa extends Environment
  case object Staging extends Environment
}

object Configuration {

  lazy val environment: Environment = {
    val environmentProperty = Option(System.getProperty("environment")).getOrElse("local").toLowerCase
    environmentProperty match {
      case "local"   ⇒ Environment.Local
      case "qa"      ⇒ Environment.Qa
      case "dev"     ⇒ Environment.Dev
      case "staging" ⇒ Environment.Staging
      case _         ⇒ sys.error(s"Environment '$environmentProperty' not known")
    }
  }

  val (host: String, authHost: String, ggHost: String) = {
    environment match {
      case Environment.Local   ⇒ ("http://localhost:7000", "http://localhost:9949", "http://localhost:9025")
      case Environment.Dev     ⇒ ("https://www-dev.tax.service.gov.uk", "https://www-dev.tax.service.gov.uk", "https://www-dev.tax.service.gov.uk")
      case Environment.Qa      ⇒ ("https://www.qa.tax.service.gov.uk", "https://www.qa.tax.service.gov.uk", "https://www.qa.tax.service.gov.uk")
      case Environment.Staging ⇒ ("https://www-staging.tax.service.gov.uk", "https://www-staging.tax.service.gov.uk", "https://www-staging.tax.service.gov.uk")
      case _                   ⇒ sys.error(s"Environment '$environment' not known")
    }
  }
}
