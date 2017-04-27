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

package uk.gov.hmrc.helptosavefrontend.controllers

import java.util.UUID
import java.util.UUID.randomUUID

import org.scalamock.scalatest.MockFactory
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import play.api.http.Status
import play.api.i18n.MessagesApi
import play.api.mvc.Result
import play.api.test.FakeRequest
import uk.gov.hmrc.helptosavefrontend.connectors.IvConnector
import uk.gov.hmrc.helptosavefrontend.models.{IvSuccessResponse, JourneyId}
import uk.gov.hmrc.play.frontend.auth.AuthenticationProviderIds
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.http.{HeaderCarrier, SessionKeys}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import uk.gov.hmrc.time.DateTimeUtils.now

import scala.concurrent.Future

class IvControllerSpec extends UnitSpec with WithFakeApplication with MockFactory {

  val ivConnector: IvConnector = mock[IvConnector]

  val jId = randomUUID().toString

  val journeyId = JourneyId(jId)

  val ivController = new IvController(ivConnector, fakeApplication.injector.instanceOf[MessagesApi]) {
    override lazy val authConnector: AuthConnector = MockAuthConnector
  }

  private def authenticatedFakeRequest =
    FakeRequest("GET", s"/register/identity-check-complete?journeyId=$jId").withSession(
      SessionKeys.sessionId -> s"session-${UUID.randomUUID()}",
      SessionKeys.lastRequestTimestamp -> now.getMillis.toString,
      SessionKeys.userId -> s"/path/to/authority",
      SessionKeys.authProvider -> AuthenticationProviderIds.VerifyProviderId
    )

  private def doRequest(jId: String) = ivController.showUpliftJourneyOutcome(authenticatedFakeRequest)

  "GET /identity-check-complete" should {

    "handle different responses from Identity-verification-frontend" in {

      val validCases =
        Table(
          ("IV Journey Result", "hts response to the user"),
          ("Success", Status.OK),
          ("Incomplete", Status.INTERNAL_SERVER_ERROR),
          ("FailedIV", Status.UNAUTHORIZED),
          ("InsufficientEvidence", Status.UNAUTHORIZED),
          ("UserAborted", Status.UNAUTHORIZED),
          ("LockedOut", Status.UNAUTHORIZED),
          ("PreconditionFailed", Status.UNAUTHORIZED),
          ("TechnicalIssue", Status.UNAUTHORIZED),
          ("Timeout", Status.UNAUTHORIZED),
          ("blah-blah", Status.INTERNAL_SERVER_ERROR)
        )

      forAll(validCases) { (ivServiceResponse: String, htsStatus: Int) ⇒

        (ivConnector.getJourneyStatus(_: JourneyId)(_: HeaderCarrier)).expects(journeyId, *)
          .returning(Future.successful(IvSuccessResponse(ivServiceResponse)))

        val response: Future[Result] = doRequest(jId)

        status(response) shouldBe htsStatus
      }
    }
  }
}
