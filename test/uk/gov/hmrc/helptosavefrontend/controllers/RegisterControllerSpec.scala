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
import play.filters.csrf.CSRF.{Token, TokenProvider}
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.config.FrontendAuthConnector
import uk.gov.hmrc.helptosavefrontend.connectors.NSIConnector.{SubmissionFailure, SubmissionSuccess}
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithCL200
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthProvider
import uk.gov.hmrc.helptosavefrontend.models.TestData.UserData.validNSIUserInfo
import uk.gov.hmrc.helptosavefrontend.services.JSONSchemaValidationService
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, NINO}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.audit.http.connector.AuditResult

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class RegisterControllerSpec extends AuthSupport with EnrolmentAndEligibilityCheckBehaviour {

  val jsonSchemaValidationService: JSONSchemaValidationService = mock[JSONSchemaValidationService]
  val mockAuditor: HTSAuditor = mock[HTSAuditor]
  val frontendAuthConnector: FrontendAuthConnector = stub[FrontendAuthConnector]
  implicit val crypto: Crypto = fakeApplication.injector.instanceOf[Crypto]

  val controller: RegisterController = new RegisterController(
    fakeApplication.injector.instanceOf[MessagesApi],
    mockHelpToSaveService,
    mockSessionCacheConnector,
    frontendAuthConnector,
    mockMetrics,
    mockAuditor)(
    ec, crypto) {
    override lazy val authConnector = mockAuthConnector
  }

  def mockCreateAccount(nSIUserInfo: NSIUserInfo)(response: Either[SubmissionFailure, SubmissionSuccess] = Right(SubmissionSuccess())): Unit =
    (mockHelpToSaveService.createAccount(_: NSIUserInfo)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nSIUserInfo, *, *)
      .returning(EitherT.fromEither[Future](response))

  def mockEnrolUser()(result: Either[String, Unit]): Unit =
    (mockHelpToSaveService.enrolUser()(_: HeaderCarrier))
      .expects(*)
      .returning(EitherT.fromEither[Future](result))

  def mockEmailUpdate(email: String)(result: Either[String, Unit]): Unit =
    (mockHelpToSaveService.storeConfirmedEmail(_: String)(_: HeaderCarrier))
      .expects(email, *)
      .returning(EitherT.fromEither[Future](result))

  def mockAccountCreationAllowed(result: Either[String, Boolean]): Unit =
    (mockHelpToSaveService.isAccountCreationAllowed()(_: HeaderCarrier))
      .expects(*)
      .returning(EitherT.fromEither[Future](result))

  def mockUpdateUserCount(result: Either[String, Unit]): Unit =
    (mockHelpToSaveService.updateUserCount()(_: HeaderCarrier))
      .expects(*)
      .returning(EitherT.fromEither[Future](result))

  def mockDecrypt(expected: String)(result: Option[String]) =
    (crypto.decrypt(_: String))
      .expects(expected)
      .returning(result.fold[Try[String]](Failure(new Exception))(Success.apply))

  def mockAudit() =
    (mockAuditor.sendEvent(_: AccountCreated, _: NINO))
      .expects(*, nino)
      .returning(Future.successful(AuditResult.Success))

  lazy val tokenProvider: TokenProvider =
    fakeApplication.injector.instanceOf[TokenProvider]

  val fakeRequest = FakeRequest().copyFakeRequest(tags = Map(
    Token.NameRequestTag → "csrfToken",
    Token.RequestTag → tokenProvider.generateToken))

  "The RegisterController" when {

    "handling getConfirmEmailPage" must {

        def doRequest(): Future[PlayResult] =
          controller.getConfirmEmailPage(fakeRequest)

      "indicate to the user that user-cap has already reached and account creation not possible" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), None))))
          mockAccountCreationAllowed(Right(false))
        }

        val result = doRequest()
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.RegisterController.getUserCapReachedPage().url)
      }

      "return the confirm email page" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), None))))
          mockAccountCreationAllowed(Right(true))
        }

        val result = doRequest()
        status(result) shouldBe Status.OK
        contentAsString(result) should include("Which email address do you want us to use for your Help to Save account?")
      }
    }

    "handling getConfirmEmailSubmit" must {

        def doRequest(newEmail: Option[String]): Future[PlayResult] = {
          newEmail.fold(
            controller.confirmEmailSubmit()(fakeRequest)
          ){ e ⇒
              controller.confirmEmailSubmit()(fakeRequest.withFormUrlEncodedBody("new-email" → e))
            }

        }

      behave like commonEnrolmentAndSessionBehaviour(() ⇒ doRequest(None))

      "redirect to confirm email if the session data shows that they have been already found to be eligible " +
        "and the form contains no new email" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), None))))
          }

          val result = doRequest(None)
          status(result) shouldBe Status.SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.RegisterController.confirmEmail(validNSIUserInfo.contactDetails.email).url)
        }

      "redirect to verify email if the session data shows that they have been already found to be eligible " +
        "and the form contains a valid new email" in {
          val newEmail = "email@test.com"

          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), None))))
          }

          val result = doRequest(Some(newEmail))
          status(result) shouldBe Status.SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.NewApplicantUpdateEmailAddressController.verifyEmail(newEmail).url)
        }

      "redirect to the confirm email page if the session data shows that they have been already found to be eligible " +
        "and the form contains a invalid new email" in {
          val invalidEmail = "not-an-email"

          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), None))))
          }

          val result = doRequest(Some(invalidEmail))
          status(result) shouldBe Status.OK
          contentAsString(result) should include("Which email")
        }

    }

    "handling getUserCapReachedPage" must {

      "return the user cap reached page" in {
        mockAuthWithNoRetrievals(AuthProvider)

        val result = controller.getUserCapReachedPage(FakeRequest())
        status(result) shouldBe Status.OK
        contentAsString(result) should include("User Limit Reached")
      }

    }

    "handling getDetailsAreIncorrect" must {

      "return the details are incorrect page" in {
        mockAuthWithNoRetrievals(AuthProvider)

        val result = controller.getDetailsAreIncorrect(FakeRequest())
        status(result) shouldBe Status.OK
        contentAsString(result) should include("nothing you can do")
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
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), None))))
            mockSessionCacheConnectorPut(HTSSession(Some(validNSIUserInfo), Some(email)))(Right(CacheMap("", Map.empty)))
            mockEmailUpdate(email)(Left(""))
          }
          await(doRequest(email))
        }

      "redirect to the create an account page if the write to keystore and the email store was successful" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), None))))
          mockSessionCacheConnectorPut(HTSSession(Some(validNSIUserInfo), Some(email)))(Right(CacheMap("", Map.empty)))
          mockEmailUpdate(email)(Right(()))
        }

        val result = doRequest(email)
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.RegisterController.getCreateAccountHelpToSavePage().url)
      }

      "return an error" when {

        "the email cannot be written to keystore" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), None))))
            mockSessionCacheConnectorPut(HTSSession(Some(validNSIUserInfo), Some(email)))(Left(""))
          }

          val result = doRequest(email)
          checkIsTechnicalErrorPage(result)
        }

        "the email cannot be written to the email store" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), None))))
            mockSessionCacheConnectorPut(HTSSession(Some(validNSIUserInfo), Some(email)))(Right(CacheMap("", Map.empty)))
            mockEmailUpdate(email)(Left(""))
          }

          val result = doRequest(email)
          checkIsTechnicalErrorPage(result)
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
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), None))))
        }

        val result = doRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.RegisterController.getConfirmEmailPage().url)
      }

      "show the user the create account page if the session data contains a confirmed email" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
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

      "retrieve the user info from session cache and indicate to the user that the creation was successful " +
        "and enrol the user if the creation was successful" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), Some(confirmedEmail)))))
            mockCreateAccount(validNSIUserInfo.updateEmail(confirmedEmail))()
            mockAudit()
            mockUpdateUserCount(Right(Unit))
            mockEnrolUser()(Right(()))
          }

          val result = doCreateAccountRequest()
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.NSIController.goToNSI().url)
        }

      "indicate to the user that the creation was successful " +
        "and even if the user couldn't be enrolled" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), Some(confirmedEmail)))))
            mockCreateAccount(validNSIUserInfo.updateEmail(confirmedEmail))()
            mockAudit()
            mockUpdateUserCount(Right(Unit))
            mockEnrolUser()(Left("Oh no"))
          }

          val result = doCreateAccountRequest()
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.NSIController.goToNSI().url)
        }

      "redirect the user to the confirm details page if the session indicates they have not done so already" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), None))))
        }

        val result = doCreateAccountRequest()
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.RegisterController.getConfirmEmailPage().url)
      }

      "indicate to the user that the creation was not successful " when {

        "the help to save service returns with an error" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), Some(confirmedEmail)))))
            mockCreateAccount(validNSIUserInfo.updateEmail(confirmedEmail))(Left(SubmissionFailure(None, "Uh oh", "Uh oh")))
          }

          val result = doCreateAccountRequest()
          checkIsTechnicalErrorPage(result)
        }
      }
    }
  }
}
