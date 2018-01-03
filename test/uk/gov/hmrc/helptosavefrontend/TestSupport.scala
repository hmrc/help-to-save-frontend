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

package uk.gov.hmrc.helptosavefrontend

import java.util.UUID

import com.codahale.metrics._
import com.kenshoo.play.metrics.{Metrics ⇒ PlayMetrics}
import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Suite}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, Configuration, Play}
import uk.gov.hmrc.helptosavefrontend.config.FrontendGlobal
import uk.gov.hmrc.helptosavefrontend.forms.EmailValidation
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.util.{NINO, NINOLogMessageTransformer, TestNINOLogMessageTransformer}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

trait TestSupport extends UnitSpec with MockFactory with BeforeAndAfterAll with ScalaFutures {
  this: Suite ⇒

  lazy val additionalConfig = Configuration()

  lazy val fakeApplication: Application =
    new GuiceApplicationBuilder()
      .configure(Configuration(
        ConfigFactory.parseString(
          """
            | metrics.enabled       = false
            | play.modules.disabled = [ "uk.gov.hmrc.helptosavefrontend.config.HealthCheckModule" ]
          """.stripMargin)
      ) ++ additionalConfig)
      .build()

  implicit lazy val ec: ExecutionContext = fakeApplication.injector.instanceOf[ExecutionContext]

  implicit val headerCarrier: HeaderCarrier =
    HeaderCarrier(sessionId = Some(SessionId(UUID.randomUUID().toString)))

  override def beforeAll() {
    Play.start(fakeApplication)
    super.beforeAll()
  }

  override def afterAll() {
    Play.stop(fakeApplication)
    super.afterAll()
  }

  private lazy val technicalErrorPageContent: String = FrontendGlobal.internalServerErrorTemplate(FakeRequest()).body

  def checkIsTechnicalErrorPage(result: Future[Result]): Unit = {
    status(result) shouldBe INTERNAL_SERVER_ERROR
    contentAsString(result) shouldBe technicalErrorPageContent
  }

  val mockMetrics = new Metrics(stub[PlayMetrics]) {
    override def timer(name: String): Timer = new Timer()

    override def counter(name: String): Counter = new Counter()

    override def histogram(name: String): Histogram = new Histogram(new UniformReservoir())
  }

  val mockEmailValidation: EmailValidation =
    new EmailValidation(Configuration(
      "email-validation.max-total-length" → Int.MaxValue,
      "email-validation.max-local-length" → Int.MaxValue,
      "email-validation.max-domain-length" → Int.MaxValue))

  implicit val transformer: NINOLogMessageTransformer = TestNINOLogMessageTransformer.transformer

}
