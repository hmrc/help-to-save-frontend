/*
 * Copyright 2020 HM Revenue & Customs
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

import java.util.UUID

import akka.stream.Materializer
import com.typesafe.config.ConfigFactory
import akka.util.Timeout
import com.codahale.metrics.{Counter, Timer}
import com.kenshoo.play.metrics.{Metrics ⇒ PlayMetrics}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.{Application, Configuration, Environment}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.inject.Injector
import play.api.mvc.{MessagesControllerComponents, Result}
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsString
import play.filters.csrf.CSRFAddToken
import uk.gov.hmrc.helptosavefrontend.config.{ErrorHandler, FrontendAppConfig}
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, NINOLogMessageTransformer, TestNINOLogMessageTransformer}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.SessionId
import scala.language.postfixOps

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

trait ControllerSpecWithGuiceApp extends ControllerSpecBase with GuiceOneAppPerSuite with I18nSupport {

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
        ) ++ additionalConfig
      )
      .build()

  override lazy val app = buildFakeApplication(additionalConfig)

  lazy val injector: Injector = app.injector

  implicit val headerCarrier: HeaderCarrier =
    HeaderCarrier(sessionId = Some(SessionId(UUID.randomUUID().toString)))

  implicit lazy val ec: ExecutionContext = injector.instanceOf[ExecutionContext]

  implicit val testMcc: MessagesControllerComponents = injector.instanceOf[MessagesControllerComponents]
  implicit val testCpd: CommonPlayDependencies = injector.instanceOf[CommonPlayDependencies]

  val messagesApi = injector.instanceOf(classOf[MessagesApi])
  val messages = request2Messages(FakeRequest())

  val commonDependencies = injector.instanceOf(classOf[CommonPlayDependencies])
  val csrfAddToken: CSRFAddToken = injector.instanceOf[play.filters.csrf.CSRFAddToken]

  implicit val mat: Materializer = mock[Materializer]

  override val mockMetrics = new Metrics(stub[PlayMetrics]) {
    override def timer(name: String): Timer = new Timer()

    override def counter(name: String): Counter = new Counter()
  }

  private lazy val technicalErrorPageContent: String =
    injector.instanceOf[ErrorHandler].internalServerErrorTemplate(FakeRequest()).body

  def checkIsTechnicalErrorPage(result: Future[Result]): Unit = {
    implicit val timeout: Timeout = Timeout(5 seconds)
    status(result) shouldBe INTERNAL_SERVER_ERROR
    contentAsString(result) shouldBe technicalErrorPageContent
  }

  lazy val testErrorHandler: ErrorHandler = injector.instanceOf[ErrorHandler]

  implicit val ninoLogMessageTransformer: NINOLogMessageTransformer = TestNINOLogMessageTransformer.transformer

  implicit lazy val appConfig: FrontendAppConfig = injector.instanceOf[FrontendAppConfig]

  implicit lazy val configuration: Configuration = injector.instanceOf[Configuration]

  implicit lazy val environment: Environment = injector.instanceOf[Environment]

  implicit val crypto: Crypto = injector.instanceOf[Crypto]
}
