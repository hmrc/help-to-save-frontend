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

import cats.data.EitherT
import cats.instances.future._
import play.api.http.Status
import play.api.i18n.MessagesApi
import play.api.mvc.{Result ⇒ PlayResult}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.connectors.NSIConnector.{SubmissionFailure, SubmissionSuccess}
import uk.gov.hmrc.helptosavefrontend.enrolment.EnrolmentStore
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithConfidence
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.services.{HelpToSaveService, JSONSchemaValidationService}
import uk.gov.hmrc.helptosavefrontend.util.{HTSAuditor, NINO, UserDetailsURI}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class RegisterControllerSpec extends TestSupport with EnrolmentAndEligibilityCheckBehaviour {

  val mockHtsService = mock[HelpToSaveService]
  val jsonSchemaValidationService = mock[JSONSchemaValidationService]
  val mockAuditor = mock[HTSAuditor]

  val controller = new RegisterController(
    fakeApplication.injector.instanceOf[MessagesApi],
    mockHtsService,
    mockSessionCacheConnector,
    mockEnrolmentService,
    fakeApplication)(
    ec) {
    override lazy val authConnector = mockAuthConnector
  }

  def mockCreateAccount(nSIUserInfo: NSIUserInfo)(response: Either[SubmissionFailure, SubmissionSuccess] = Right(SubmissionSuccess())): Unit =
    (mockHtsService.createAccount(_: NSIUserInfo)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nSIUserInfo, *, *)
      .returning(EitherT.fromEither[Future](response))

  def mockEnrolUser(nino: NINO)(result: Either[String,Unit]): Unit =
    (mockEnrolmentService.enrolUser(_: NINO)(_: HeaderCarrier, _: ExecutionContext))
    .expects(nino, *, *)
    .returning(EitherT.fromEither[Future](result))


  "The RegisterController" when {

    "handling getConfirmDetailsPage" must {

      def doRequest(): Future[PlayResult] = controller.getConfirmDetailsPage(FakeRequest())

      testCommonEnrolmentAndSessionBehaviour(doRequest)

      "show the users details if the user has not already enrolled and " +
        "the session data shows that they have been already found to be eligible" in {
        inSequence {
          mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
          mockEnrolmentCheck(nino)(Right(EnrolmentStore.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo)))))
        }

        val result = doRequest()
        status(result) shouldBe Status.OK
        contentType(result) shouldBe Some("text/html")
        charset(result) shouldBe Some("utf-8")
        contentAsString(result) should include(validNSIUserInfo.forename)
        contentAsString(result) should include(validNSIUserInfo.surname)
      }

    }


    "handling a getCreateAccountHelpToSave" must {


      def doRequest(): Future[PlayResult] = controller.getCreateAccountHelpToSavePage(FakeRequest())

      testCommonEnrolmentAndSessionBehaviour(doRequest)

      "return 200  if the user has not already enrolled and " +
        "the session data shows that they have been already found to be eligible" in {
        inSequence {
          mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
          mockEnrolmentCheck(nino)(Right(EnrolmentStore.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo)))))
        }

        val result = controller.getCreateAccountHelpToSavePage(FakeRequest())
        status(result) shouldBe Status.OK
        contentType(result) shouldBe Some("text/html")
        charset(result) shouldBe Some("utf-8")
      }
    }

    "creating an account" must {
      def doCreateAccountRequest(): Future[PlayResult] = controller.createAccountHelpToSave(FakeRequest())

      testCommonEnrolmentAndSessionBehaviour(doCreateAccountRequest)


      "retrieve the user info from session cache and post it using " +
        "the help to save service" in {
        inSequence {
          mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
          mockEnrolmentCheck(nino)(Right(EnrolmentStore.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo)))))
          mockCreateAccount(validNSIUserInfo)(Left(SubmissionFailure(None, "", "")))
        }
        val result = Await.result(doCreateAccountRequest(), 5.seconds)
        status(result) shouldBe Status.OK
      }


      "indicate to the user that the creation was successful " +
        "and enrol the user if the creation was successful" in {
        inSequence {
          mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
          mockEnrolmentCheck(nino)(Right(EnrolmentStore.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo)))))
          mockCreateAccount(validNSIUserInfo)()
          mockEnrolUser(nino)(Right(()))
        }

        val result = doCreateAccountRequest()

        val html = contentAsString(result)
        html should include("Successfully created account")
      }

      "indicate to the user that the creation was successful " +
        "and even if the user couldn't be enrolled" in {
        inSequence {
          mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
          mockEnrolmentCheck(nino)(Right(EnrolmentStore.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo)))))
          mockCreateAccount(validNSIUserInfo)()
          mockEnrolUser(nino)(Left("Oh no"))
        }

        val result = doCreateAccountRequest()
        val html = contentAsString(result)
        html should include("Successfully created account")
      }

      "indicate to the user that the creation was not successful " when {

        "the help to save service returns with an error" in {
          inSequence {
            mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
            mockEnrolmentCheck(nino)(Right(EnrolmentStore.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo)))))
            mockCreateAccount(validNSIUserInfo)(Left(SubmissionFailure(None, "Uh oh", "Uh oh")))
          }

          val result = doCreateAccountRequest()
          val html = contentAsString(result)
          html should include("Account creation failed")
        }
      }
    }
  }





}
