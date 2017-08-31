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
import uk.gov.hmrc.helptosavefrontend.config.FrontendAuthConnector
import uk.gov.hmrc.helptosavefrontend.connectors.NSIConnector.{SubmissionFailure, SubmissionSuccess}
import uk.gov.hmrc.helptosavefrontend.controllers.RegisterController.NSIUserInfoOps
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithCL200
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.services.JSONSchemaValidationService
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, EmailVerificationParams, HTSAuditor, NINO}
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class RegisterControllerSpec extends AuthSupport with EnrolmentAndEligibilityCheckBehaviour {

  val jsonSchemaValidationService = mock[JSONSchemaValidationService]
  val mockAuditor = mock[HTSAuditor]
  val frontendAuthConnector = stub[FrontendAuthConnector]
  implicit val crypto = fakeApplication.injector.instanceOf[Crypto]

  val controller = new RegisterController(
    fakeApplication.injector.instanceOf[MessagesApi],
    mockHelpToSaveService,
    mockSessionCacheConnector,
    fakeApplication,
    frontendAuthConnector)(
    ec, crypto) {
    override lazy val authConnector = mockAuthConnector
  }

  def mockCreateAccount(nSIUserInfo: NSIUserInfo)(response: Either[SubmissionFailure, SubmissionSuccess] = Right(SubmissionSuccess())): Unit =
    (mockHelpToSaveService.createAccount(_: NSIUserInfo)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nSIUserInfo, *, *)
      .returning(EitherT.fromEither[Future](response))

  def mockEnrolUser(nino: NINO)(result: Either[String, Unit]): Unit =
    (mockHelpToSaveService.enrolUser(_: NINO)(_: HeaderCarrier))
      .expects(nino, *)
      .returning(EitherT.fromEither[Future](result))

  def mockEmailUpdate(email: String, nino: NINO)(result: Either[String, Unit]): Unit =
    (mockHelpToSaveService.storeConfirmedEmail(_: String, _: NINO)(_: HeaderCarrier))
      .expects(email, nino, *)
      .returning(EitherT.fromEither[Future](result))

  def mockDecrypt(expected: String)(result: Option[String]) =
    (crypto.decrypt(_: String))
      .expects(expected)
      .returning(result.fold[Try[String]](Failure(new Exception))(Success.apply))

  "The RegisterController" when {

    "handling getConfirmDetailsPage" must {

        def doRequest(): Future[PlayResult] = controller.getConfirmDetailsPage(None)(FakeRequest())

        def doRequestWithQueryParam(p: String): Future[PlayResult] = controller.getConfirmDetailsPage(Some(p))(FakeRequest())

      behave like commonEnrolmentAndSessionBehaviour(doRequest)

      "show the users details if the user has not already enrolled and " +
        "the session data shows that they have been already found to be eligible" in {
          inSequence {
            mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockEnrolmentCheck(nino)(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), None))))
          }

          val result = doRequest()
          status(result) shouldBe Status.OK
          contentType(result) shouldBe Some("text/html")
          charset(result) shouldBe Some("utf-8")
          contentAsString(result) should include(validNSIUserInfo.forename)
          contentAsString(result) should include(validNSIUserInfo.surname)
        }

      "show the users details with the verified user email address " +
        "if the user has not already enrolled and " +
        "the session data shows that they have been already found to be eligible " +
        "and the user has clicked on the verify email link sent to them by the email verification service " in {
          val testEmail = "email@gmail.com"
          val theNino = "AE1234XXX"
          inSequence {
            mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockEnrolmentCheck(nino)(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo.copy(nino = theNino)), None))))
          }
          val params = EmailVerificationParams(theNino, testEmail)
          val result = doRequestWithQueryParam(params.encode().replaceAll("%2b", "+"))
          status(result) shouldBe Status.OK
          contentAsString(result) should include(testEmail)
        }

      "return an OK status when the user has not already enrolled and the given nino doesn't match the session nino" in {
        val testEmail = "email@gmail.com"
        val theNino = "AE1234XXX"
        inSequence {
          mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck(nino)(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), None))))
        }
        val params = EmailVerificationParams(theNino, testEmail)
        val result = doRequestWithQueryParam(params.encode())
        status(result) shouldBe Status.OK
        contentAsString(result) should include("Email verification error")
      }

      "return an OK status when the link has been corrupted or is incorrect" in {
        inSequence {
          mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck(nino)(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), None))))
        }
        val result = doRequestWithQueryParam("corrupt-link")
        status(result) shouldBe Status.OK
        contentAsString(result) should include("Email verification error")
      }
    }

    "handling a confirmEmail" must {

      val email = "email"

        def doRequest(email: String): Future[PlayResult] =
          controller.confirmEmail(email)(FakeRequest())

      behave like commonEnrolmentAndSessionBehaviour(() ⇒ doRequest(email))

      "write the email to keystore and the email store if the user has not already enrolled and " +
        "the session data shows that they have been already found to be eligible" in {
          inSequence {
            mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockEnrolmentCheck(nino)(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), None))))
            mockSessionCacheConnectorPut(HTSSession(Some(validNSIUserInfo), Some(email)))(Right(CacheMap("", Map.empty)))
            mockEmailUpdate(email, nino)(Left(""))
          }
          await(doRequest(email))
        }

      "redirect to the create an account page if the write to keystore and the email store was successful" in {
        inSequence {
          mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck(nino)(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), None))))
          mockSessionCacheConnectorPut(HTSSession(Some(validNSIUserInfo), Some(email)))(Right(CacheMap("", Map.empty)))
          mockEmailUpdate(email, nino)(Right(()))
        }

        val result = doRequest(email)
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.RegisterController.getCreateAccountHelpToSavePage().url)
      }

      "return an error" when {

        "the email cannot be written to keystore" in {
          inSequence {
            mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockEnrolmentCheck(nino)(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), None))))
            mockSessionCacheConnectorPut(HTSSession(Some(validNSIUserInfo), Some(email)))(Left(""))
          }

          val result = doRequest(email)
          status(result) shouldBe Status.INTERNAL_SERVER_ERROR
        }

        "the email cannot be written to the email store" in {
          inSequence {
            mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockEnrolmentCheck(nino)(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), None))))
            mockSessionCacheConnectorPut(HTSSession(Some(validNSIUserInfo), Some(email)))(Right(CacheMap("", Map.empty)))
            mockEmailUpdate(email, nino)(Left(""))
          }

          val result = doRequest(email)
          status(result) shouldBe Status.INTERNAL_SERVER_ERROR
        }
      }
    }

    "handling a getCreateAccountHelpToSave" must {

      val email = "email"

        def doRequest(): Future[PlayResult] =
          controller.getCreateAccountHelpToSavePage()(FakeRequest())

      behave like commonEnrolmentAndSessionBehaviour(() ⇒ doRequest())

      "redirect the user to the confirm details page if there is no email in the session data" in {
        inSequence {
          mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck(nino)(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), None))))
        }

        val result = doRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.RegisterController.getConfirmDetailsPage(None).url)
      }

      "show the user the create account page if the session data contains a confirmed email" in {
        inSequence {
          mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck(nino)(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), Some(email)))))
        }

        val result = doRequest()
        status(result) shouldBe OK
        contentAsString(result) should include("Accept and create account")
      }

    }

    "creating an account" must {
      val confirmedEmail = "confirmed"

        def doCreateAccountRequest(): Future[PlayResult] = controller.createAccountHelpToSave(FakeRequest())

      behave like commonEnrolmentAndSessionBehaviour(doCreateAccountRequest)

      "retrieve the user info from session cache and post it with the confirmed email using " +
        "the help to save service" in {
          inSequence {
            mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockEnrolmentCheck(nino)(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), Some(confirmedEmail)))))
            mockCreateAccount(validNSIUserInfo.updateEmail(confirmedEmail))(Left(SubmissionFailure(None, "", "")))
          }
          val result = Await.result(doCreateAccountRequest(), 5.seconds)
          status(result) shouldBe Status.OK
        }

      "indicate to the user that the creation was successful " +
        "and enrol the user if the creation was successful" in {
          inSequence {
            mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockEnrolmentCheck(nino)(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), Some(confirmedEmail)))))
            mockCreateAccount(validNSIUserInfo.updateEmail(confirmedEmail))()
            mockEnrolUser(nino)(Right(()))
          }

          val result = doCreateAccountRequest()

          val html = contentAsString(result)
          html should include("Successfully created account")
        }

      "indicate to the user that the creation was successful " +
        "and even if the user couldn't be enrolled" in {
          inSequence {
            mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockEnrolmentCheck(nino)(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), Some(confirmedEmail)))))
            mockCreateAccount(validNSIUserInfo.updateEmail(confirmedEmail))()
            mockEnrolUser(nino)(Left("Oh no"))
          }

          val result = doCreateAccountRequest()
          val html = contentAsString(result)
          html should include("Successfully created account")
        }

      "redirect the user to the confirm details page if the session indicates they have not done so already" in {
        inSequence {
          mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck(nino)(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), None))))
        }

        val result = doCreateAccountRequest()
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.RegisterController.getConfirmDetailsPage(None).url)
      }

      "indicate to the user that the creation was not successful " when {

        "the help to save service returns with an error" in {
          inSequence {
            mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockEnrolmentCheck(nino)(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), Some(confirmedEmail)))))
            mockCreateAccount(validNSIUserInfo.updateEmail(confirmedEmail))(Left(SubmissionFailure(None, "Uh oh", "Uh oh")))
          }

          val result = doCreateAccountRequest()
          val html = contentAsString(result)
          html should include("Account creation failed")
        }
      }
    }
  }
}
