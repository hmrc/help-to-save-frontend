/*
 * Copyright 2023 HM Revenue & Customs
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

import org.mockito.ArgumentMatchersSugar.*
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavefrontend.connectors.IvConnector
import uk.gov.hmrc.helptosavefrontend.models.HTSSession
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthProvider
import uk.gov.hmrc.helptosavefrontend.models.iv.{IvSuccessResponse, JourneyId}
import uk.gov.hmrc.helptosavefrontend.views.html.iv._

import java.net.URLEncoder
import java.util.UUID.randomUUID
import scala.concurrent.Future

class IvControllerSpec extends ControllerSpecWithGuiceApp with SessionStoreBehaviourSupport with AuthSupport {

  val ivConnector: IvConnector = mock[IvConnector]

  val journeyId = JourneyId(randomUUID().toString)

  lazy val ivController = new IvController(
    mockSessionStore,
    ivConnector,
    mockAuthConnector,
    mockMetrics,
    testCpd,
    testMcc,
    testErrorHandler,
    testMaintenanceSchedule,
    injector.instanceOf[iv_success],
    injector.instanceOf[failed_iv],
    injector.instanceOf[insufficient_evidence],
    injector.instanceOf[locked_out],
    injector.instanceOf[user_aborted],
    injector.instanceOf[time_out],
    injector.instanceOf[technical_iv_issues],
    injector.instanceOf[precondition_failed]
  )

  val continueURL = "continue-here"

  lazy val mockPutContinueURLInSessionCache =
    mockSessionStorePut(HTSSession(None, None, None, None, Some(continueURL)))(Right(()))

  lazy val mockPutIVURLInSessionCache =
    mockSessionStorePut(HTSSession(None, None, None, Some(appConfig.ivUrl(continueURL)), None))(Right(()))

  def mockIvConnector(journeyId: JourneyId, ivServiceResponse: String): Unit =
    ivConnector.getJourneyStatus(journeyId)(*, *) returns Future.successful(IvSuccessResponse.fromString(ivServiceResponse))

  private def doRequest() =
    ivController.journeyResult(URLEncoder.encode(continueURL, "UTF-8"), Some(journeyId.Id))(FakeRequest())

  "GET /iv/journey-result" when {

    def noSessionPutBehaviour(expectedRedirectURL: => String, ivServiceResponse: String): Unit =
      "redirect to the correct URL" in {
          mockAuthWithNoRetrievals(AuthProvider)
          mockIvConnector(journeyId, ivServiceResponse)

        val result = doRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(expectedRedirectURL)
      }

    def sessionPutIVURLBehaviour(expectedRedirectURL: => String, ivServiceResponse: String): Unit = {

      "redirect to the correct URL if the write to session cache is successful" in {
          mockAuthWithNoRetrievals(AuthProvider)
          mockIvConnector(journeyId, ivServiceResponse)
          mockSessionStorePut(HTSSession(None, None, None, Some(appConfig.ivUrl(continueURL)), None))(Right(()))

        val result = doRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(expectedRedirectURL)
      }

      "show an error page if the write to session cache is unsuccessful" in {
          mockAuthWithNoRetrievals(AuthProvider)
          mockIvConnector(journeyId, ivServiceResponse)
          mockSessionStorePut(HTSSession(None, None, None, Some(appConfig.ivUrl(continueURL)), None))(Left(""))
        val result = doRequest()
        checkIsTechnicalErrorPage(result)
      }

    }

    "handling success responses" must {

      "redirect to the correct URL if the write to session cache is successful" in {
          mockAuthWithNoRetrievals(AuthProvider)
          mockIvConnector(journeyId, "Success")
          mockSessionStorePut(HTSSession(None, None, None, None, Some(continueURL)))(Right(()))

        val result = doRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.IvController.getIVSuccessful.url)
      }

      "show an error page if the write to session cache is unsuccessful" in {
          mockAuthWithNoRetrievals(AuthProvider)
          mockIvConnector(journeyId, "Success")
          mockSessionStorePut(HTSSession(None, None, None, None, Some(continueURL)))(Left(""))
        val result = doRequest()
        checkIsTechnicalErrorPage(result)
      }

      "show an error page if URL contains unexpected characters" in {
        val badURL = "javascript%3Aalert%281%29%3B&journeyId=b9087faf-adcb-40ae-9e74-1e8afe35df69"
          mockAuthWithNoRetrievals(AuthProvider)
          mockIvConnector(journeyId, "Success")
          mockSessionStorePut(HTSSession(None, None, None, None, Some(badURL)))(Left(""))
        val result = ivController.journeyResult(badURL, Some(journeyId.Id))(FakeRequest())
        checkIsTechnicalErrorPage(result)
      }

    }

    "handling incomplete responses" must {
      behave like sessionPutIVURLBehaviour(routes.IvController.getTechnicalIssue.url, "Incomplete")
    }

    "handling failed iv responses" must {
      behave like sessionPutIVURLBehaviour(routes.IvController.getFailedIV.url, "FailedIV")
    }

    "handling failed matching responses" must {
      behave like sessionPutIVURLBehaviour(
        "https://www.gov.uk/government/organisations/hm-revenue-customs/contact/help-to-save-scheme",
        "FailedMatching"
      )
    }

    "handling insufficient evidence responses" must {
      behave like noSessionPutBehaviour(routes.IvController.getInsufficientEvidence.url, "InsufficientEvidence")
    }

    "handling user aborted responses" must {
      behave like sessionPutIVURLBehaviour(routes.IvController.getUserAborted.url, "UserAborted")

    }

    "handling locked out responses" must {
      behave like noSessionPutBehaviour(routes.IvController.getLockedOut.url, "LockedOut")

    }

    "handling precondition failed responses" must {
      behave like noSessionPutBehaviour(routes.IvController.getPreconditionFailed.url, "PreconditionFailed")

    }

    "handling technical issue responses" must {
      behave like sessionPutIVURLBehaviour(routes.IvController.getTechnicalIssue.url, "TechnicalIssue")

    }

    "handling timeout responses" must {
      behave like sessionPutIVURLBehaviour(routes.IvController.getTimedOut.url, "Timeout")

    }

    "handling unknown responses" must {

      behave like sessionPutIVURLBehaviour(routes.IvController.getTechnicalIssue.url, "???")

    }

    "handles the case where no iv response for a given journeyId" in {
        mockAuthWithNoRetrievals(AuthProvider)
        mockPutIVURLInSessionCache
      val result =
        ivController.journeyResult(URLEncoder.encode(continueURL, "UTF-8"), None)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.IvController.getTechnicalIssue.url)
    }
  }

  "The IV controller" when {

    def testIndividualPage(
      name: String, // scalastyle:ignore method.length
      getResult: () => Future[Result],
      mockSessionCacheBehaviour: Option[() => Unit],
      expectedUrl: Option[String],
      defaultUrl: Option[String]
    )(successChecks: (Option[String], Future[Result]) => Unit): Unit =
      s"handling $name" must {

        s"show the correct $name page" in {
          mockSessionCacheBehaviour.fold(
            mockAuthWithNoRetrievals(AuthProvider)
          ) { behaviour =>
              mockAuthWithNoRetrievals(AuthProvider)
              behaviour()
          }

          successChecks(expectedUrl, getResult())
        }

        if (mockSessionCacheBehaviour.isDefined) {

          "show an error page" when {

            "session cache retrieval fails" in {
                mockAuthWithNoRetrievals(AuthProvider)
                mockSessionStoreGet(Left(""))

              checkIsTechnicalErrorPage(getResult())
            }

            "there is no session data" in {
                mockAuthWithNoRetrievals(AuthProvider)
                mockSessionStoreGet(Right(None))

              successChecks(defaultUrl, getResult())
            }

            "the data required is not present in the session" in {
                mockAuthWithNoRetrievals(AuthProvider)
                mockSessionStoreGet(Right(Some(HTSSession(None, None, None, None, None))))

              checkIsTechnicalErrorPage(getResult())
            }
          }
        }
      }

    val url = "my-url"

    def contentAsStringWithAmpersandsEscaped(result: Future[Result]): String =
      contentAsString(result).replaceAll("&amp;", "&")

    testIndividualPage(
      "IV successful",
      () => ivController.getIVSuccessful()(FakeRequest()),
      Some(() => mockSessionStoreGet(Right(Some(HTSSession(None, None, None, None, Some(url)))))),
      Some(url),
      Some(ivController.eligibilityUrl)
    ) { (maybeUrl, result) =>
      status(result) shouldBe OK
      maybeUrl.foreach(u => contentAsStringWithAmpersandsEscaped(result) should include(u))
      contentAsString(result) should include("ve verified your identity")
    }

    testIndividualPage(
      "failed iv",
      () => ivController.getFailedIV()(FakeRequest()),
      Some(() => mockSessionStoreGet(Right(Some(HTSSession(None, None, None, Some(url), None))))),
      Some(url),
      Some(ivController.defaultIVUrl)
    ) { (maybeUrl, result) =>
      status(result) shouldBe OK
      maybeUrl.foreach(u => contentAsStringWithAmpersandsEscaped(result) should include(u))
      contentAsString(result) should include("You did not answer all the questions correctly")
    }

    testIndividualPage(
      "insufficient evidence",
      () => ivController.getInsufficientEvidence()(FakeRequest()),
      None,
      None,
      None
    ) { (_, result) =>
      status(result) shouldBe OK
      contentAsString(result) should include("be able to apply for a Help to Save account by phone, after")
    }

    testIndividualPage(
      "locked out",
      () => ivController.getLockedOut()(FakeRequest()),
      None,
      None,
      None
    ) { (_, result) =>
      status(result) shouldBe OK
      contentAsString(result) should include("You have tried to verify your identity too many times")
    }

    testIndividualPage(
      "user aborted",
      () => ivController.getUserAborted()(FakeRequest()),
      Some(() => mockSessionStoreGet(Right(Some(HTSSession(None, None, None, Some(url), None))))),
      Some(url),
      Some(ivController.defaultIVUrl)
    ) { (maybeUrl, result) =>
      status(result) shouldBe OK
      maybeUrl.foreach(u => contentAsStringWithAmpersandsEscaped(result) should include(u))
      contentAsString(result) should include("You have not provided enough information")
    }

    testIndividualPage(
      "timed out",
      () => ivController.getTimedOut()(FakeRequest()),
      Some(() => mockSessionStoreGet(Right(Some(HTSSession(None, None, None, Some(url), None))))),
      Some(url),
      Some(ivController.defaultIVUrl)
    ) { (maybeUrl, result) =>
      status(result) shouldBe OK
      maybeUrl.foreach(u => contentAsStringWithAmpersandsEscaped(result) should include(u))
      contentAsString(result) should include("Your session has ended")
    }

    testIndividualPage(
      "technical issue",
      () => ivController.getTechnicalIssue()(FakeRequest()),
      Some(() => mockSessionStoreGet(Right(Some(HTSSession(None, None, None, Some(url), None))))),
      Some(url),
      Some(ivController.defaultIVUrl)
    ) { (maybeUrl, result) =>
      status(result) shouldBe OK
      maybeUrl.foreach(u => contentAsStringWithAmpersandsEscaped(result) should include(u))
      contentAsString(result) should include("Something went wrong")
    }

    testIndividualPage(
      "precondition failed",
      () => ivController.getPreconditionFailed()(FakeRequest()),
      None,
      None,
      None
    ) { (_, result) =>
      status(result) shouldBe OK
      contentAsString(result) should include("not able to use this service")
    }

  }

}
