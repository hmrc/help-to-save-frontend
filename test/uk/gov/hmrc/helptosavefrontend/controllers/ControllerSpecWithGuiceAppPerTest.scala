/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.helptosavefrontend.controllers

import com.codahale.metrics.{Counter, Timer}
import com.kenshoo.play.metrics.{Metrics => PlayMetrics}
import com.typesafe.config.ConfigFactory
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.inject.Injector
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Configuration}
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics

import scala.concurrent.ExecutionContext

trait ControllerSpecWithGuiceAppPerTest extends ControllerSpecBase with GuiceOneAppPerTest with I18nSupport {

  lazy val additionalConfig = Configuration()

  def buildFakeApplication(additionalConfig: Configuration): Application =
    new GuiceApplicationBuilder()
      .configure(
        Configuration(
          ConfigFactory.parseString("""
                                      | metrics.jvm = false
                                      | metrics.enabled = true
                                      | play.modules.disabled = [ "play.api.mvc.CookiesModule",
                                      |   "uk.gov.hmrc.helptosavefrontend.config.HealthCheckModule",
                                      |   "akka.event.slf4j.Slf4jLogger"
                                      | ]
                                      | mongodb.session.expireAfter = 5 seconds
          """.stripMargin)
        ).withFallback(additionalConfig)
      )
      .build()

  override def fakeApplication = buildFakeApplication(additionalConfig)

  lazy val injector: Injector = fakeApplication.injector

  implicit lazy val ec: ExecutionContext = injector.instanceOf[ExecutionContext]

  val messagesApi = injector.instanceOf(classOf[MessagesApi])

  override val mockMetrics = new Metrics(stub[PlayMetrics]) {
    override def timer(name: String): Timer = new Timer()

    override def counter(name: String): Counter = new Counter()
  }

}
