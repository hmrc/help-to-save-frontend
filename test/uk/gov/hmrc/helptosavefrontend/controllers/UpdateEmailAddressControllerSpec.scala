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

import java.time.LocalDate

import play.api.http.Status
import play.api.i18n.MessagesApi
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavefrontend.config.{FrontendAuthConnector, WSHttp}
import uk.gov.hmrc.helptosavefrontend.connectors.EmailVerificationConnector
import uk.gov.hmrc.helptosavefrontend.controllers.UpdateEmailAddressController.NSIUserInfoOps
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithCL200
import uk.gov.hmrc.helptosavefrontend.models.NSIUserInfo.ContactDetails
import uk.gov.hmrc.helptosavefrontend.models.VerifyEmailError.{AlreadyVerified, BackendError, RequestNotValidError, VerificationServiceUnavailable}
import uk.gov.hmrc.helptosavefrontend.models.{EnrolmentStatus, HTSSession, NSIUserInfo, VerifyEmailError, validNSIUserInfo}
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, EmailVerificationParams}
import uk.gov.hmrc.play.http.HeaderCarrier
import play.api.mvc.{Result ⇒ PlayResult}
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.http.cache.client.CacheMap

import scala.concurrent.Future

class UpdateEmailAddressControllerSpec extends AuthSupport with EnrolmentAndEligibilityCheckBehaviour {

  lazy val injector = fakeApplication.injector
  lazy val request = FakeRequest()
  implicit val crypto = fakeApplication.injector.instanceOf[Crypto]

  def messagesApi = injector.instanceOf[MessagesApi]

  lazy val messages = messagesApi.preferred(request)

  val frontendAuthConnector = stub[FrontendAuthConnector]

  val mockEmailVerificationConnector = mock[EmailVerificationConnector]

  val mockHttp: WSHttp = mock[WSHttp]

  lazy val controller = new UpdateEmailAddressController(mockSessionCacheConnector, mockHelpToSaveService, frontendAuthConnector, mockEmailVerificationConnector
  )(fakeApplication, fakeApplication.injector.instanceOf[MessagesApi], crypto, ec) {

    override val authConnector = mockAuthConnector
  }

  def mockEmailVerificationConn(result: Either[VerifyEmailError, Unit]) = {
    (mockEmailVerificationConnector.verifyEmail(_: String, _: String)(_: HeaderCarrier)).expects(*, *, *)
      .returning(Future.successful(result))
  }

  "The UpdateEmailAddressController" when {

    "getting the update your email page " must {

        def getResult(): Future[Result] = controller.getUpdateYourEmailAddress(FakeRequest())

      behave like commonEnrolmentAndSessionBehaviour(getResult)

      "return the update your email page if the user is not already enrolled and the " +
        "session data indicates that they are eligible" in {

          inSequence {
            mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockEnrolmentCheck(nino)(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), None))))
          }

          val result = getResult()
          status(result) shouldBe Status.OK
          contentAsString(result) should include(messages("hts.email-verification.title"))
        }

      "return the you're not eligible page if the user is not already enrolled and the " +
        "session data indicates that they are ineligible" in {
          inSequence {
            mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockEnrolmentCheck(nino)(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None))))
          }
          val result = getResult()
          status(result) shouldBe Status.OK
          contentAsString(result) should include("not eligible")
        }
    }

  }

  "onSubmit" should {
    "return the check your email page with a status of Ok, given a valid email address " in {
      val fakePostRequest = FakeRequest().withFormUrlEncodedBody("value" → "email@gmail.com")
      inSequence {
        mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
        mockEnrolmentCheck(nino)(Right(EnrolmentStatus.NotEnrolled))
        mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None))))
        mockEmailVerificationConn(Right(()))
      }
      val result = await(controller.onSubmit()(fakePostRequest))
      status(result) shouldBe Status.OK
      contentAsString(result).contains(messagesApi("hts.email-verification.check-your-email.title")) shouldBe true
      contentAsString(result).contains(messagesApi("hts.email-verification.check-your-email.content")) shouldBe true
    }

    "return an AlreadyVerified status and redirect the user to the email-verify-error page," +
      " given an email address of an already verified user " in {
        val fakePostRequest = FakeRequest().withFormUrlEncodedBody("value" → "email@gmail.com")
        inSequence {
          mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck(nino)(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None))))
          mockEmailVerificationConn(Left(AlreadyVerified))
        }
        val result = await(controller.onSubmit()(fakePostRequest))
        status(result) shouldBe Status.OK
        contentAsString(result).contains(messagesApi("hts.email-verification.error.title")) shouldBe true
        contentAsString(result).contains(messagesApi("hts.email-verification.error.already-verified.content")) shouldBe true
      }

    "return an OK status and redirect the user to the email_verify_error page with request not valid message" in {
      val fakePostRequest = FakeRequest().withFormUrlEncodedBody("value" → "email@gmail.com")
      inSequence {
        mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
        mockEnrolmentCheck(nino)(Right(EnrolmentStatus.NotEnrolled))
        mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None))))
        mockEmailVerificationConn(Left(RequestNotValidError))
      }
      val result = controller.onSubmit()(fakePostRequest)
      status(result) shouldBe Status.OK
      contentAsString(result).contains(messagesApi("hts.email-verification.error.title")) shouldBe true
      contentAsString(result).contains(messagesApi("hts.email-verification.error.request-not-valid.content")) shouldBe true
    }

    "return an OK status and redirect the user to the email_verify_error page with verification service unavailable message" in {
      val fakePostRequest = FakeRequest().withFormUrlEncodedBody("value" → "email@gmail.com")
      inSequence {
        mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
        mockEnrolmentCheck(nino)(Right(EnrolmentStatus.NotEnrolled))
        mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None))))
        mockEmailVerificationConn(Left(VerificationServiceUnavailable))
      }
      val result = controller.onSubmit()(fakePostRequest)
      status(result) shouldBe Status.OK
      contentAsString(result).contains(messagesApi("hts.email-verification.error.title")) shouldBe true
      contentAsString(result).contains(messagesApi("hts.email-verification.error.verification-service-unavailable.content")) shouldBe true
    }

    "return an OK status and redirect the user to the email_verify_error page with backend error message" in {
      val fakePostRequest = FakeRequest().withFormUrlEncodedBody("value" → "email@gmail.com")
      inSequence {
        mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
        mockEnrolmentCheck(nino)(Right(EnrolmentStatus.NotEnrolled))
        mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None))))
        mockEmailVerificationConn(Left(BackendError))
      }
      val result = controller.onSubmit()(fakePostRequest)
      status(result) shouldBe Status.OK
      contentAsString(result).contains(messagesApi("hts.email-verification.error.title")) shouldBe true
      contentAsString(result).contains(messagesApi("hts.email-verification.error.backend-error.content")) shouldBe true
    }
  }

  "emailVerified" should {

      def doRequestWithQueryParam(p: String): Future[PlayResult] = controller.emailVerified(p)(FakeRequest())

    "show the check and confirm your details page showing the users details with the verified user email address " +
      "if the user has not already enrolled and " +
      "the session data shows that they have been already found to be eligible " +
      "and the user has clicked on the verify email link sent to them by the email verification service and the nino from auth " +
      "matches that passed in via the params" in {
        val testEmail = "email@gmail.com"

        inSequence {
          mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), None))))
          mockSessionCacheConnectorPut(HTSSession(Some(validNSIUserInfo.updateEmail(testEmail)), None))(Right(()))
        }
        val params = EmailVerificationParams(validNSIUserInfo.nino, testEmail)
        val result = doRequestWithQueryParam(params.encode())
        status(result) shouldBe Status.OK
        contentAsString(result) should include(testEmail)
        contentAsString(result).contains(messagesApi("hts.register.check-and-confirm-your-details.title")) shouldBe true
        contentAsString(result).contains(messagesApi("hts.register.check-and-confirm-your-details.p-1")) shouldBe true
      }

    "return an Internal Server Error status when the user has not already enrolled and the given nino doesn't match the session nino" in {
      val testEmail = "email@gmail.com"
      inSequence {
        mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
        mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), None))))
      }
      val params = EmailVerificationParams("AE1234XXX", testEmail)
      val result = doRequestWithQueryParam(params.encode())
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR
    }

    "return an OK status when the link has been corrupted or is incorrect" in {
      inSequence {
        mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
      }
      val result = doRequestWithQueryParam("corrupt-link")
      status(result) shouldBe Status.OK
      contentAsString(result) should include("Email verification error")
    }
  }

}
