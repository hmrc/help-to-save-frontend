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

  case object Esit extends Environment

}

object Configuration {

  lazy val environment: Environment = {
    val environmentProperty = Option(System.getProperty("environment")).getOrElse("local").toLowerCase
    environmentProperty match {
      case "local"   ⇒ Environment.Local
      case "qa"      ⇒ Environment.Qa
      case "dev"     ⇒ Environment.Dev
      case "staging" ⇒ Environment.Staging
      case "esit"    ⇒ Environment.Esit
      case _         ⇒ sys.error(s"Environment '$environmentProperty' not known")
    }
  }

  val (host: String, authHost: String, ggHost: String, feedbackHost) = {
    environment match {
      case Environment.Local ⇒ ("http://localhost:7000", "http://localhost:9949", "http://localhost:9025", "http://localhost:9250")
      case Environment.Dev | Environment.Qa | Environment.Staging | Environment.Esit ⇒
        val rootUrl = Option(System.getProperty("rootUrl")).getOrElse(sys.error("no rootUrl supplied")).toLowerCase
        List.fill(4)(rootUrl) // scalastyle:ignore magic.number
      case _ ⇒ sys.error(s"Environment '$environment' not known")
    }
  }
}
