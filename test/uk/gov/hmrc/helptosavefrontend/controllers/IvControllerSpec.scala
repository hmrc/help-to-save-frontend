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

import java.net.URLEncoder
import java.util.UUID.randomUUID

import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import play.api.http.Status._
import play.api.i18n.MessagesApi
import play.api.test.FakeRequest
import uk.gov.hmrc.helptosavefrontend.connectors.{IvConnector, SessionCacheConnector}
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthProvider
import uk.gov.hmrc.helptosavefrontend.models.iv.{IvSuccessResponse, JourneyId}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class IvControllerSpec extends AuthSupport {

  val ivConnector: IvConnector = mock[IvConnector]

  val journeyId = JourneyId(randomUUID().toString)

  val fakeNino = "WM123456C"

  def mockIvConnector(journeyId: JourneyId, ivServiceResponse: String): Unit =
    (ivConnector.getJourneyStatus(_: JourneyId)(_: HeaderCarrier)).expects(journeyId, *)
      .returning(Future.successful(IvSuccessResponse.fromString(ivServiceResponse)))

  val mockSessionCacheConnector: SessionCacheConnector = mock[SessionCacheConnector]

  lazy val ivController = new IvController(mockSessionCacheConnector,
                                           ivConnector,
                                           fakeApplication.injector.instanceOf[MessagesApi],
                                           mockAuthConnector,
                                           mockMetrics)

  private val fakeRequest = FakeRequest("GET", s"/iv/journey-result?journeyId=${journeyId.Id}")

  val continueURL = "continue-here!!"

  private def doRequest() = ivController.journeyResult(URLEncoder.encode(continueURL, "UTF-8"))(fakeRequest)

  "GET /iv/journey-result" should {

    "handle different responses from identity-verification-frontend" in {

      val validCases =
        Table(
          ("IV Journey Result", "hts response to the user"),
          ("Success", OK),
          ("Incomplete", INTERNAL_SERVER_ERROR),
          ("FailedIV", UNAUTHORIZED),
          ("FailedMatching", UNAUTHORIZED),
          ("InsufficientEvidence", UNAUTHORIZED),
          ("UserAborted", UNAUTHORIZED),
          ("LockedOut", UNAUTHORIZED),
          ("PreconditionFailed", UNAUTHORIZED),
          ("TechnicalIssue", UNAUTHORIZED),
          ("Timeout", UNAUTHORIZED),
          ("blah-blah", INTERNAL_SERVER_ERROR)
        )

      forAll(validCases) { (ivServiceResponse: String, htsStatus: Int) ⇒
        mockAuthWithNINORetrievalWithSuccess(AuthProvider)(mockedNINORetrieval)
        mockIvConnector(journeyId, ivServiceResponse)

        val responseFuture = doRequest()

        val result = Await.result(responseFuture, 3.seconds)

        status(result) should be(htsStatus)
      }
    }

    "handles the case where no iv response for a given journeyId" in {

      mockAuthWithNINORetrievalWithSuccess(AuthProvider)(mockedNINORetrieval)

      val responseFuture =
        ivController.journeyResult(URLEncoder.encode(continueURL, "UTF-8"))(
          FakeRequest("GET", "/iv/journey-result"))

      val result = Await.result(responseFuture, 3.seconds)

      status(result) should be(UNAUTHORIZED)
    }
  }
}
