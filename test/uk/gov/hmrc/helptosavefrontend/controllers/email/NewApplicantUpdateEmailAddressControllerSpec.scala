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

package uk.gov.hmrc.helptosavefrontend.controllers.email

import java.net.URLDecoder

import play.api.http.Status
import play.api.i18n.{Messages, MessagesApi}
import play.api.inject.Injector
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.config.{FrontendAuthConnector, WSHttp}
import uk.gov.hmrc.helptosavefrontend.connectors.EmailVerificationConnector
import uk.gov.hmrc.helptosavefrontend.controllers.{AuthSupport, EnrolmentAndEligibilityCheckBehaviour}
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithCL200
import uk.gov.hmrc.helptosavefrontend.models.VerifyEmailError.{AlreadyVerified, BackendError, RequestNotValidError, VerificationServiceUnavailable}
import uk.gov.hmrc.helptosavefrontend.models.{EnrolmentStatus, HTSSession, SuspiciousActivity, VerifyEmailError, validNSIUserInfo}
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, EmailVerificationParams, NINO}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditResult

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class NewApplicantUpdateEmailAddressControllerSpec extends AuthSupport with EnrolmentAndEligibilityCheckBehaviour {

  lazy val injector: Injector = fakeApplication.injector
  lazy val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  implicit val crypto: Crypto = fakeApplication.injector.instanceOf[Crypto]

  def messagesApi: MessagesApi = injector.instanceOf[MessagesApi]

  lazy val messages: Messages = messagesApi.preferred(request)

  val frontendAuthConnector: FrontendAuthConnector = stub[FrontendAuthConnector]

  val mockEmailVerificationConnector: EmailVerificationConnector = mock[EmailVerificationConnector]

  val mockHttp: WSHttp = mock[WSHttp]

  val mockAuditor = mock[HTSAuditor]

  lazy val controller: NewApplicantUpdateEmailAddressController =
    new NewApplicantUpdateEmailAddressController(
      mockSessionCacheConnector,
      mockHelpToSaveService,
      frontendAuthConnector,
      mockEmailVerificationConnector,
      mockMetrics,
      mockAuditor
    )(fakeApplication, fakeApplication.injector.instanceOf[MessagesApi], crypto, ec) {

      override val authConnector = mockAuthConnector
    }

  def mockEmailVerificationConn(nino: String, email: String)(result: Either[VerifyEmailError, Unit]) = {
    (mockEmailVerificationConnector.verifyEmail(_: String, _: String)(_: HeaderCarrier, _: ExecutionContext, _: UserType))
      .expects(nino, email, *, *, UserType.NewApplicant)
      .returning(Future.successful(result))
  }

  def mockAudit() =
    (mockAuditor.sendEvent(_: SuspiciousActivity, _: NINO))
      .expects(*, *)
      .returning(Future.successful(AuditResult.Success))

  "The UpdateEmailAddressController" when {

    "getting the update your email page " must {

        def getResult(): Future[Result] = controller.getUpdateYourEmailAddress(FakeRequest())

      behave like commonEnrolmentAndSessionBehaviour(getResult)

      "return the update your email page if the user is not already enrolled and the " +
        "session data indicates that they are eligible" in {

          inSequence {
            mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
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
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None))))
          }
          val result = getResult()
          status(result) shouldBe Status.OK
          contentAsString(result) should include("not eligible")
        }
    }

  }

  "onSubmit" should {

    val email = "email@gmail.com"

    "return the check your email page with a status of Ok, given a valid email address " in {
      val fakePostRequest = FakeRequest().withFormUrlEncodedBody("new-email-address" → email)
      inSequence {
        mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
        mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None))))
        mockEmailVerificationConn(nino, email)(Right(()))
      }
      val result = await(controller.onSubmit()(fakePostRequest))
      status(result) shouldBe Status.OK
      contentAsString(result).contains(messagesApi("hts.email-verification.check-your-email.title")) shouldBe true
      contentAsString(result).contains(messagesApi("hts.email-verification.check-your-email.content")) shouldBe true
    }

    "return an AlreadyVerified status and redirect the user to email verified page," +
      " given an email address of an already verified user " in {
        val email = "email@gmail.com"
        val fakePostRequest = FakeRequest().withFormUrlEncodedBody("new-email-address" → email)
        inSequence {
          mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None))))
          mockEmailVerificationConn(nino, email)(Left(AlreadyVerified))
        }
        val result = await(controller.onSubmit()(fakePostRequest))
        status(result) shouldBe Status.SEE_OTHER

        val redirectURL = redirectLocation(result)

        redirectURL
          .getOrElse(fail("Could not find redirect location"))
          .split('=')
          .toList match {
            case _ :: param :: Nil ⇒
              EmailVerificationParams.decode(URLDecoder.decode(param, "UTF-8")) match {
                case Success(params) ⇒
                  params.nino shouldBe nino
                  params.email shouldBe email

                case Failure(e) ⇒ fail(s"Could not decode email verification parameters string: $param", e)
              }

            case _ ⇒ fail(s"Unexpected redirect location found: $redirectURL")
          }

      }

    "return an OK status and redirect the user to the email_verify_error page with request not valid message" in {
      val fakePostRequest = FakeRequest().withFormUrlEncodedBody("new-email-address" → email)
      inSequence {
        mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
        mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None))))
        mockEmailVerificationConn(nino, email)(Left(RequestNotValidError))
      }
      val result = controller.onSubmit()(fakePostRequest)
      status(result) shouldBe Status.OK
      contentAsString(result).contains(messagesApi("hts.email-verification.error.title")) shouldBe true
      contentAsString(result).contains(messagesApi("hts.email-verification.error.request-not-valid.content")) shouldBe true
    }

    "return an OK status and redirect the user to the email_verify_error page with verification service unavailable message" in {
      val fakePostRequest = FakeRequest().withFormUrlEncodedBody("new-email-address" → email)
      inSequence {
        mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
        mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None))))
        mockEmailVerificationConn(nino, email)(Left(VerificationServiceUnavailable))
      }
      val result = controller.onSubmit()(fakePostRequest)
      status(result) shouldBe Status.OK
      contentAsString(result).contains(messagesApi("hts.email-verification.error.title")) shouldBe true
      contentAsString(result).contains(messagesApi("hts.email-verification.error.verification-service-unavailable.content")) shouldBe true
    }

    "return an OK status and redirect the user to the email_verify_error page with backend error message" in {
      val fakePostRequest = FakeRequest().withFormUrlEncodedBody("new-email-address" → email)
      inSequence {
        mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
        mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None))))
        mockEmailVerificationConn(nino, email)(Left(BackendError))
      }
      val result = controller.onSubmit()(fakePostRequest)
      status(result) shouldBe Status.OK
      contentAsString(result).contains(messagesApi("hts.email-verification.error.title")) shouldBe true
      contentAsString(result).contains(messagesApi("hts.email-verification.error.backend-error.content")) shouldBe true
    }

    "return a BadRequest if there are errors in the form" in {
      val fakePostRequest = FakeRequest().withFormUrlEncodedBody("crap" → "other-crap")
      inSequence {
        mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
        mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None))))
      }
      val result = await(controller.onSubmit()(fakePostRequest))
      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) should include(messages("hts.email-verification.title"))
    }
  }

  "emailVerified" should {
    val testEmail = "email@gmail.com"

      def doRequestWithQueryParam(p: String): Future[Result] = controller.emailVerified(p)(FakeRequest())

    "show the check and confirm your details page showing the users details with the verified user email address " +
      "if the user has not already enrolled and " +
      "the session data shows that they have been already found to be eligible " +
      "and the user has clicked on the verify email link sent to them by the email verification service and the nino from auth " +
      "matches that passed in via the params" in {

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

    "return an OK status when the link has been corrupted or is incorrect" in {
      inSequence {
        mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
        mockAudit()
      }
      val result = doRequestWithQueryParam("corrupt-link")
      status(result) shouldBe Status.OK
      contentAsString(result) should include("Email verification error")
    }

    "return an OK status with a not eligible view when an ineligible user comes in via email verified" in {
      inSequence {
        mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
        mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None))))
      }
      val params = EmailVerificationParams(validNSIUserInfo.nino, testEmail)
      val result = doRequestWithQueryParam(params.encode())
      status(result) shouldBe Status.OK
      contentAsString(result) should include("not eligible for Help to Save")
    }

    "return an Internal Server Error" when {

        def test(mockActions: ⇒ Unit, nino: String, email: String) = {
          mockActions
          val params = EmailVerificationParams(nino, email)
          val result = doRequestWithQueryParam(params.encode())
          checkIsTechnicalErrorPage(result)
        }

      "the user has not already enrolled and the given nino doesn't match the session nino" in {
        test(inSequence {
          mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), None))))
        },
          "AE1234XXX",
             testEmail
        )
      }

      "the sessionCacheConnector.get method returns an error" in {
        test(inSequence {
          mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionCacheConnectorGet(Left("An error occurred"))
        },
             validNSIUserInfo.nino,
             testEmail
        )
      }

      "the sessionCacheConnector.put method returns an error" in {
        test(inSequence {
          mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), None))))
          mockSessionCacheConnectorPut(HTSSession(Some(validNSIUserInfo.updateEmail(testEmail)), None))(Left("An error occurred"))
        },
             validNSIUserInfo.nino,
             testEmail
        )
      }

      "the user has missing info and they do not have a session" in {
        test(inSequence {
          mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrivalsMissingUserInfo)
          mockSessionCacheConnectorGet(Right(None))
        },
             validNSIUserInfo.nino,
             testEmail
        )

      }
    }

  }

}
