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
import play.api.test.FakeRequest
import play.api.{Configuration, Environment}
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.helptosavefrontend.connectors.{IvConnector, SessionCacheConnector}
import uk.gov.hmrc.helptosavefrontend.models.iv.{IvSuccessResponse, JourneyId}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class IvControllerSpec extends UnitSpec with WithFakeApplication with MockFactory {

  val ivConnector: IvConnector = mock[IvConnector]

  val journeyId = JourneyId(randomUUID().toString)

  implicit val ec: ExecutionContext = fakeApplication.injector.instanceOf[ExecutionContext]

  val fakeNino = "WM123456C"

  def mockIvConnector(journeyId: JourneyId, ivServiceResponse: String): Unit =
    (ivConnector.getJourneyStatus(_: JourneyId)(_: HeaderCarrier)).expects(journeyId, *)
      .returning(Future.successful(IvSuccessResponse.fromString(ivServiceResponse)))

  val mockSessionCacheConnector: SessionCacheConnector = mock[SessionCacheConnector]

  private val mockAuthConnector = mock[PlayAuthConnector]

  private def mockAuthConnectorResult() = {
    (mockAuthConnector.authorise[Unit](_: Predicate, _: Retrieval[Unit])(_: HeaderCarrier))
      .expects(AuthProviders(GovernmentGateway), EmptyRetrieval, *).returning(Future.successful(()))
  }

  lazy val ivController = new IvController(mockSessionCacheConnector,
    ivConnector,
    fakeApplication.injector.instanceOf[MessagesApi]) {
    override def authConnector: AuthConnector = mockAuthConnector
  }

  private val fakeRequest = FakeRequest("GET", s"/register/identity-check-complete?journeyId=${journeyId.Id}")

  private def doRequest() = ivController.showUpliftJourneyOutcome()(fakeRequest)

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
        mockAuthConnectorResult()
        mockIvConnector(journeyId, ivServiceResponse)

        val responseFuture = doRequest()

        val result = Await.result(responseFuture, 3.seconds)

        status(result) should be(htsStatus)
      }
    }
  }
}
