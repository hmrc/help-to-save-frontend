/*
 * Copyright 2021 HM Revenue & Customs
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

import java.net.URLDecoder
import java.time.LocalDate
import java.util.UUID

import cats.data.EitherT
import cats.instances.future._
import org.scalamock.scalatest.MockFactory
import play.api.http.Status
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.authorise.{EmptyPredicate, Predicate}
import uk.gov.hmrc.auth.core.retrieve.EmptyRetrieval
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.connectors.EmailVerificationConnector
import uk.gov.hmrc.helptosavefrontend.models.EnrolmentStatus.{Enrolled, NotEnrolled}
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth._
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.account.{Account, Blocking}
import uk.gov.hmrc.helptosavefrontend.models.email.VerifyEmailError
import uk.gov.hmrc.helptosavefrontend.models.email.VerifyEmailError.{AlreadyVerified, OtherError}
import uk.gov.hmrc.helptosavefrontend.models.userinfo.NSIPayload
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.{Email, EmailVerificationParams, NINO}
import uk.gov.hmrc.helptosavefrontend.views.html.accessibility.accessibility_statement
import uk.gov.hmrc.helptosavefrontend.views.html.closeaccount.close_account_are_you_sure
import uk.gov.hmrc.helptosavefrontend.views.html.email.accountholder.check_your_email
import uk.gov.hmrc.helptosavefrontend.views.html.email.{update_email_address, we_updated_your_email}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditResult

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

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
