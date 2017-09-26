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

import cats.data.EitherT
import cats.instances.future._
import play.api.http.Status
import play.api.i18n.MessagesApi
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavefrontend.connectors.{EmailVerificationConnector, NSIConnector}
import uk.gov.hmrc.helptosavefrontend.controllers.AuthSupport
import uk.gov.hmrc.helptosavefrontend.models.EnrolmentStatus.{Enrolled, NotEnrolled}
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithCL200
import uk.gov.hmrc.helptosavefrontend.models.VerifyEmailError.{AlreadyVerified, BackendError, RequestNotValidError, VerificationServiceUnavailable}
import uk.gov.hmrc.helptosavefrontend.models.{EnrolmentStatus, NSIUserInfo, VerifyEmailError}
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, Email, EmailVerificationParams, NINO}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class AccountHolderUpdateEmailAddressControllerSpec extends AuthSupport {

  implicit lazy val crypto: Crypto = fakeApplication.injector.instanceOf[Crypto]

  val mockHelpToSaveService = mock[HelpToSaveService]

  val mockEmailVerificationConnector = mock[EmailVerificationConnector]

  val mockNSIConnector = mock[NSIConnector]

  def mockEnrolmentCheck()(result: Either[String, EnrolmentStatus]): Unit =
    (mockHelpToSaveService.getUserEnrolmentStatus()(_: HeaderCarrier))
      .expects(*)
      .returning(EitherT.fromEither[Future](result))

  def mockEmailGet()(result: Either[String, Option[String]]): Unit =
    (mockHelpToSaveService.getConfirmedEmail()(_: HeaderCarrier))
      .expects(*)
      .returning(EitherT.fromEither[Future](result))

  def mockStoreEmail(email: Email)(result: Either[String, Unit]): Unit =
    (mockHelpToSaveService.storeConfirmedEmail(_: Email)(_: HeaderCarrier))
      .expects(email, *)
      .returning(EitherT.fromEither[Future](result))

  lazy val controller = new AccountHolderUpdateEmailAddressController(
    mockHelpToSaveService,
    mockAuthConnector,
    mockEmailVerificationConnector,
    mockNSIConnector
  )(fakeApplication, crypto, fakeApplication.injector.instanceOf[MessagesApi], ec) {
    override val authConnector = mockAuthConnector
  }

  def mockEmailVerificationConn(nino: String, email: String)(result: Either[VerifyEmailError, Unit]) =
    (mockEmailVerificationConnector.verifyEmail(_: String, _: String)(_: HeaderCarrier, _: UserType))
      .expects(nino, email, *, UserType.AccountHolder)
      .returning(Future.successful(result))

  def mockUpdateEmailWithNSI(userInfo: NSIUserInfo)(result: Either[String, Unit]): Unit =
    (mockNSIConnector.updateEmail(_: NSIUserInfo)(_: HeaderCarrier, _: ExecutionContext))
      .expects(userInfo, *, *)
      .returning(EitherT.fromEither[Future](result))

  "The AccountHolderUpdateEmailAddressController" when {

    "handling requests to update email addresses" must {

        def getUpdateYourEmailAddress(): Future[Result] =
          controller.getUpdateYourEmailAddress()(FakeRequest())

      behave like (commonEnrolmentBehaviour(() ⇒ getUpdateYourEmailAddress()))

      "show a page which allows the user to change their email if they are already " +
        "enrolled and we have an email stored for them" in {
          inSequence{
            mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockEnrolmentCheck()(Right(Enrolled(true)))
            mockEmailGet()(Right(Some("email")))
          }

          val result = getUpdateYourEmailAddress()
          status(result) shouldBe OK
          contentAsString(result) should include("Update your email")
        }
    }

    "handling formupdate email forms submits" must {

      val enrolled = EnrolmentStatus.Enrolled(true)

      val email = "email@test.com"

      lazy val messagesApi: MessagesApi = fakeApplication.injector.instanceOf[MessagesApi]

      val fakePostRequest = FakeRequest().withFormUrlEncodedBody("new-email-address" → email)

        def submit(email: String): Future[Result] =
          controller.onSubmit()(FakeRequest().withFormUrlEncodedBody("new-email-address" → email))

      behave like commonEnrolmentBehaviour(() ⇒ submit("email"))

      "return the check your email page with a status of Ok, given a valid email address " in {
        inSequence {
          mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck()(Right(enrolled))
          mockEmailGet()(Right(Some("email")))
          mockEmailVerificationConn(nino, email)(Right(()))
        }
        val result = await(controller.onSubmit()(fakePostRequest))
        status(result) shouldBe Status.OK
        contentAsString(result).contains(messagesApi("hts.email-verification.check-your-email.title")) shouldBe true
        contentAsString(result).contains(messagesApi("hts.email-verification.check-your-email.content")) shouldBe true
      }

      "return an AlreadyVerified status and redirect the user to email verified page," +
        " given an email address of an already verified user " in {
          inSequence {
            mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockEnrolmentCheck()(Right(enrolled))
            mockEmailGet()(Right(Some("email")))
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
          mockEnrolmentCheck()(Right(enrolled))
          mockEmailGet()(Right(Some("email")))
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
          mockEnrolmentCheck()(Right(enrolled))
          mockEmailGet()(Right(Some("email")))
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
          mockEnrolmentCheck()(Right(enrolled))
          mockEmailGet()(Right(Some("email")))
          mockEmailVerificationConn(nino, email)(Left(BackendError))
        }
        val result = controller.onSubmit()(fakePostRequest)
        status(result) shouldBe Status.OK
        contentAsString(result).contains(messagesApi("hts.email-verification.error.title")) shouldBe true
        contentAsString(result).contains(messagesApi("hts.email-verification.error.backend-error.content")) shouldBe true
      }

    }

    "handling verified emails" must {

      val verifiedEmail = "new email"
      val emailVerificationParams = EmailVerificationParams(nino, verifiedEmail)

        def verifyEmail(params: String): Future[Result] =
          controller.emailVerified(params)(FakeRequest())

      behave like commonEnrolmentBehaviour(() ⇒ verifyEmail(emailVerificationParams.encode()))

      "show a success page if the NINO in the URL matches the NINO from auth, the update with " +
        "NS&I is successful and the email is successfully updated in mongo" in {
          inSequence{
            mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockEnrolmentCheck()(Right(Enrolled(true)))
            mockEmailGet()(Right(Some("email")))
            mockUpdateEmailWithNSI(nsiUserInfo.updateEmail(verifiedEmail))(Right(()))
            mockStoreEmail(verifiedEmail)(Right(()))
          }

          val result = verifyEmail(emailVerificationParams.encode())
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.AccountHolderUpdateEmailAddressController.getEmailUpdated().url)
        }

      "redirect to NS&I" when {

        "the NINO in the URL matches the NINO from auth, the update with " +
          "NS&I is successful but the email is not successfully updated in mongo" in {
            inSequence{
              mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
              mockEnrolmentCheck()(Right(Enrolled(true)))
              mockEmailGet()(Right(Some("email")))
              mockUpdateEmailWithNSI(nsiUserInfo.updateEmail(verifiedEmail))(Right(()))
              mockStoreEmail(verifiedEmail)(Left(""))
            }

            val result = verifyEmail(emailVerificationParams.encode())
            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some(uk.gov.hmrc.helptosavefrontend.controllers.routes.NSIController.goToNSI().url)
          }

      }

      "return an error" when {

        "the parameter in the URL cannot be decoded" in {
          mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)

          val result = verifyEmail("random crap")
          status(result) shouldBe OK
          contentAsString(result) should include("There is an error with this verification link")
        }

        "the NINO in the URL does not match the NINO from auth" in {
          inSequence{
            mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockEnrolmentCheck()(Right(Enrolled(true)))
            mockEmailGet()(Right(Some("email")))
          }

          val result = verifyEmail(emailVerificationParams.copy(nino = "other nino").encode())
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }

        "there is missing user info from auth" in {
          inSequence{
            mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedMissingUserInfo)
            mockEnrolmentCheck()(Right(Enrolled(true)))
            mockEmailGet()(Right(Some("email")))
          }

          val result = verifyEmail(emailVerificationParams.copy(nino = "other nino").encode())
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }

        "the call to NS&I to update the email is unsuccessful" in {
          inSequence{
            mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockEnrolmentCheck()(Right(Enrolled(true)))
            mockEmailGet()(Right(Some("email")))
            mockUpdateEmailWithNSI(nsiUserInfo.updateEmail(verifiedEmail))(Left(""))
          }

          val result = verifyEmail(emailVerificationParams.encode())
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.AccountHolderUpdateEmailAddressController.getEmailUpdateError().url)
        }

      }
    }
  }

  def commonEnrolmentBehaviour(getResult: () ⇒ Future[Result]): Unit = { // scalastyle:ignore method.length

    "return an error" when {

      "the user has no NINO" in {
        mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedMissingNinoEnrolment)

        status(getResult()) shouldBe INTERNAL_SERVER_ERROR
      }

      "there is an error getting the enrolment status" in {
        inSequence{
          mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck()(Left(""))
        }

        status(getResult()) shouldBe INTERNAL_SERVER_ERROR
      }

      "there is an error getting the confirmed email" in {
        inSequence{
          mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockEmailGet()(Left(""))
        }

        status(getResult()) shouldBe INTERNAL_SERVER_ERROR
      }

      "the user is not enrolled" in {
        inSequence{
          mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck()(Right(NotEnrolled))
          mockEmailGet()(Right(Some("email")))
        }

        status(getResult()) shouldBe INTERNAL_SERVER_ERROR

      }

      "the user is enrolled but has no stored email" in {
        inSequence{
          mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockEmailGet()(Right(None))
        }

        status(getResult()) shouldBe INTERNAL_SERVER_ERROR
      }

    }
  }

}
