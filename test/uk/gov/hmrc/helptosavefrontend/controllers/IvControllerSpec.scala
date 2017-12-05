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
import play.api.i18n.MessagesApi
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig.ivUrl
import uk.gov.hmrc.helptosavefrontend.connectors.{IvConnector, SessionCacheConnector}
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthProvider
import uk.gov.hmrc.helptosavefrontend.models.iv.{IvSuccessResponse, JourneyId}
import uk.gov.hmrc.helptosavefrontend.util.urlEncode
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

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

  val continueURL = "continue-here"

  private def doRequest() = ivController.journeyResult(URLEncoder.encode(continueURL, "UTF-8"))(fakeRequest)

  "GET /iv/journey-result" should {

    "handle different responses from identity-verification-frontend" in {

      val validCases =
        Table[String, Option[String]](
          ("IV Journey Result", "hts response to the user"),
          ("Success", Some(routes.IvController.getIVSuccessful(urlEncode(continueURL)).url)),
          ("Incomplete", None),
          ("FailedIV", Some(routes.IvController.getFailedIV(ivUrl(continueURL)).url)),
          ("FailedMatching", Some(routes.IvController.getFailedMatching(ivUrl(continueURL)).url)),
          ("InsufficientEvidence", Some(routes.IvController.getInsufficientEvidence().url)),
          ("UserAborted", Some(routes.IvController.getUserAborted(ivUrl(continueURL)).url)),
          ("LockedOut", Some(routes.IvController.getLockedOut().url)),
          ("PreconditionFailed", Some(routes.IvController.getPreconditionFailed().url)),
          ("TechnicalIssue", Some(routes.IvController.getTechnicalIssue(ivUrl(continueURL)).url)),
          ("Timeout", Some(routes.IvController.getTimedOut(ivUrl(continueURL)).url)),
          ("blah-blah", None)
        )

      forAll(validCases) { (ivServiceResponse: String, redirectURL: Option[String]) ⇒
        mockAuthWithNINORetrievalWithSuccess(AuthProvider)(mockedNINORetrieval)
        mockIvConnector(journeyId, ivServiceResponse)

        val result = doRequest()

        redirectURL.fold {
          checkIsTechnicalErrorPage(result)
        } { redirectURL ⇒
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(redirectURL)
        }
      }
    }

    "handles the case where no iv response for a given journeyId" in {

      mockAuthWithNINORetrievalWithSuccess(AuthProvider)(mockedNINORetrieval)

      val result =
        ivController.journeyResult(URLEncoder.encode(continueURL, "UTF-8"))(
          FakeRequest("GET", "/iv/journey-result"))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.IvController.getTechnicalIssue(ivUrl(continueURL)).url)
    }
  }

  "The IvController" must {

      def test(name:      String,
               getResult: ⇒ Future[Result])(checks: Future[Result] ⇒ Unit): Unit = {
        s"show the correct $name page" in {
          mockAuthWithNINORetrievalWithSuccess(AuthProvider)(mockedNINORetrieval)
          checks(getResult)
        }
      }

    val url = "my-url"

    test(
      "IV successful",
      ivController.getIVSuccessful(url)(FakeRequest())
    ){ result ⇒
        status(result) shouldBe OK
        contentAsString(result) should include(url)
        contentAsString(result) should include("ve verified your identity")
      }

    test(
      "failed matching",
      ivController.getFailedMatching(url)(FakeRequest())
    ){ result ⇒
        status(result) shouldBe UNAUTHORIZED
        contentAsString(result) should include(url)
        contentAsString(result) should include("need to try again and check you entered your details correctly")
      }

    test(
      "failed iv",
      ivController.getFailedIV(url)(FakeRequest())
    ){ result ⇒
        status(result) shouldBe UNAUTHORIZED
        contentAsString(result) should include(url)
        contentAsString(result) should include("You did not answer all the questions correctly")
      }

    test(
      "insufficient evidence",
      ivController.getInsufficientEvidence()(FakeRequest())
    ){ result ⇒
        status(result) shouldBe UNAUTHORIZED
        contentAsString(result) should include("be able to apply for a Help to Save account by phone, after")
      }

    test(
      "locked out",
      ivController.getLockedOut()(FakeRequest())
    ){ result ⇒
        status(result) shouldBe UNAUTHORIZED
        contentAsString(result) should include("You have tried to verify your identity too many times")
      }

    test(
      "user aborted",
      ivController.getUserAborted(url)(FakeRequest())
    ){ result ⇒
        status(result) shouldBe UNAUTHORIZED
        contentAsString(result) should include(url)
        contentAsString(result) should include("You have not provided enough information")
      }

    test(
      "timed out",
      ivController.getTimedOut(url)(FakeRequest())
    ){ result ⇒
        status(result) shouldBe UNAUTHORIZED
        contentAsString(result) should include(url)
        contentAsString(result) should include("Your session has ended")
      }

    test(
      "technical issue",
      ivController.getTechnicalIssue(url)(FakeRequest())
    ){ result ⇒
        status(result) shouldBe UNAUTHORIZED
        contentAsString(result) should include(url)
        contentAsString(result) should include("Something went wrong")
      }

    test(
      "precondition failed",
      ivController.getPreconditionFailed()(FakeRequest())
    ){ result ⇒
        status(result) shouldBe UNAUTHORIZED
        contentAsString(result) should include("not able to use this service")
      }

  }
}
