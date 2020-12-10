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

import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.authorise.{EmptyPredicate, Predicate}
import uk.gov.hmrc.auth.core.retrieve.EmptyRetrieval
import uk.gov.hmrc.helptosavefrontend.views.html.accessibility.accessibility_statement
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class AccessibilityStatementControllerSpec
  extends ControllerSpecWithGuiceApp with CSRFSupport with SessionStoreBehaviourSupport with AuthSupport {

  private val fakeRequest = FakeRequest("GET", "/")

  lazy val view = app.injector.instanceOf[accessibility_statement]

  lazy val controller = new AccessibilityStatementController(
    mockAuthConnector,
    mockMetrics,
    testCpd,
    testMcc,
    testErrorHandler,
    testMaintenanceSchedule,
    view
  ) {
    override val authConnector = mockAuthConnector
  }

  "AccessibilityStatementController" must {
    (mockAuthConnector
      .authorise(_: Predicate, _: EmptyRetrieval.type)(_: HeaderCarrier, _: ExecutionContext))
      .expects(EmptyPredicate, EmptyRetrieval, *, *)
      .returning(Future.successful(Unit))

    "on get display accessibility statement" in {

      val result = controller.get(FakeRequest().withSession())
      status(result) shouldBe OK
    }
  }

}
