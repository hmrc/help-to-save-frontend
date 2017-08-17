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

package uk.gov.hmrc.helptosavefrontend

import java.util.UUID
import java.util.concurrent.TimeUnit.SECONDS

import akka.util.Timeout
import com.typesafe.config.{Config, ConfigValueFactory}
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Suite}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{Headers, Session}
import play.api.test.FakeApplication
import play.api.{Application, Configuration, Play}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext

trait TestSupport extends UnitSpec with MockFactory with BeforeAndAfterAll with ScalaFutures {
  this: Suite ⇒

  lazy val fakeApplication: Application =
    new GuiceApplicationBuilder()
      .configure("metrics.enabled" → false)
      .build()

  //implicit val timeout = Timeout(5, SECONDS)

  def fakeApplicationWithConfig(additionalConfig: Config): Application =
    new GuiceApplicationBuilder()
      .configure(Configuration(additionalConfig.withValue("metrics.enabled", ConfigValueFactory.fromAnyRef(false))))
      .build()

  implicit val ec: ExecutionContext = fakeApplication.injector.instanceOf[ExecutionContext]

  implicit val headerCarrier: HeaderCarrier =
    HeaderCarrier.fromHeadersAndSession(Headers(), Some(Session(Map("sessionId" -> UUID.randomUUID().toString))))

  val config: Config = fakeApplication.configuration.underlying

  override def beforeAll() {
    Play.start(fakeApplication)
    super.beforeAll()
  }

  override def afterAll() {
    Play.stop(fakeApplication)
    super.afterAll()
  }

}
