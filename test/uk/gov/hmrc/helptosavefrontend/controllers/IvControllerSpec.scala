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

import java.util.UUID.randomUUID

import org.scalamock.scalatest.MockFactory
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import play.api.http.Status._
import play.api.i18n.MessagesApi
import play.api.mvc.Result
import play.api.test.FakeRequest
import uk.gov.hmrc.helptosavefrontend.connectors.{IvConnector, SessionCacheConnector}
import uk.gov.hmrc.helptosavefrontend.models.iv.{IvSuccessResponse, JourneyId}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.{ExecutionContext, Future}

class IvControllerSpec extends UnitSpec with WithFakeApplication with MockFactory {

  val ivConnector: IvConnector = mock[IvConnector]

  val journeyId = JourneyId(randomUUID().toString)

  implicit val ec: ExecutionContext = fakeApplication.injector.instanceOf[ExecutionContext]

  val fakeNino = "WM123456C"

  def mockIvConnector(journeyId: JourneyId, ivServiceResponse: String): Unit =
    (ivConnector.getJourneyStatus(_: JourneyId)(_: HeaderCarrier)).expects(journeyId, *)
      .returning(Future.successful(IvSuccessResponse.fromString(ivServiceResponse)))

  val mockSessionCacheConnector: SessionCacheConnector = mock[SessionCacheConnector]

  val ivController = new IvController(mockSessionCacheConnector, ivConnector, fakeApplication.injector.instanceOf[MessagesApi])

  private def authenticatedFakeRequest = FakeRequest("GET", s"/register/identity-check-complete?journeyId=${journeyId.Id}")

  private def doRequest() = ivController.showUpliftJourneyOutcome(authenticatedFakeRequest)

  "GET /identity-check-complete" should {

    "handle different responses from Identity-verification-frontend" in {

      val validCases =
        Table(
          ("IV Journey Result", "hts response to the user"),
          ("Success", OK),
          ("Incomplete", INTERNAL_SERVER_ERROR),
          ("FailedIV", UNAUTHORIZED),
          ("InsufficientEvidence", UNAUTHORIZED),
          ("UserAborted", UNAUTHORIZED),
          ("LockedOut", UNAUTHORIZED),
          ("PreconditionFailed", UNAUTHORIZED),
          ("TechnicalIssue", UNAUTHORIZED),
          ("Timeout", UNAUTHORIZED),
          ("blah-blah", INTERNAL_SERVER_ERROR)
        )

      forAll(validCases) { (ivServiceResponse: String, htsStatus: Int) ⇒

        mockIvConnector(journeyId, ivServiceResponse)

        val response: Future[Result] = doRequest()

        status(response) shouldBe htsStatus
      }
    }
  }
}
