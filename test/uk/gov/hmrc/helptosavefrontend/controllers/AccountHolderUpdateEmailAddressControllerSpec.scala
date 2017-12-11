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

import java.net.URLDecoder

import cats.data.EitherT
import cats.instances.future._
import play.api.http.Status
import play.api.i18n.MessagesApi
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.connectors.{EmailVerificationConnector, NSIConnector}
import uk.gov.hmrc.helptosavefrontend.models.EnrolmentStatus.{Enrolled, NotEnrolled}
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth._
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.email.VerifyEmailError
import uk.gov.hmrc.helptosavefrontend.models.email.VerifyEmailError.{AlreadyVerified, OtherError}
import uk.gov.hmrc.helptosavefrontend.models.userinfo.NSIUserInfo
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, Email, EmailVerificationParams, NINO, NINOLogMessageTransformer, TestNINOLogMessageTransformer}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditResult

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class AccountHolderUpdateEmailAddressControllerSpec extends AuthSupport with CSRFSupport {

  implicit lazy val crypto: Crypto = fakeApplication.injector.instanceOf[Crypto]

  val mockHelpToSaveService = mock[HelpToSaveService]

  val mockEmailVerificationConnector = mock[EmailVerificationConnector]

  val mockNSIConnector = mock[NSIConnector]

  val mockAuditor = mock[HTSAuditor]

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

  def mockAuditSuspiciousActivity() =
    (mockAuditor.sendEvent(_: SuspiciousActivity, _: NINO))
      .expects(*, nino)
      .returning(Future.successful(AuditResult.Success))

  def mockAuditEmailChanged() =
    (mockAuditor.sendEvent(_: EmailChanged, _: NINO))
      .expects(*, nino)
      .returning(Future.successful(AuditResult.Success))

  lazy val controller = new AccountHolderUpdateEmailAddressController(
    mockHelpToSaveService,
    mockAuthConnector,
    mockEmailVerificationConnector,
    mockNSIConnector,
    mockMetrics,
    mockAuditor
  )(fakeApplication, crypto, mockEmailValidation, fakeApplication.injector.instanceOf[MessagesApi], transformer) {
    override val authConnector = mockAuthConnector
  }

  def mockEmailVerificationConn(nino: String, email: String, firstName: String)(result: Either[VerifyEmailError, Unit]) =
    (mockEmailVerificationConnector.verifyEmail(_: String, _: String, _: String, _: Boolean)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nino, email, firstName, false, *, *)
      .returning(Future.successful(result))

  def mockUpdateEmailWithNSI(userInfo: NSIUserInfo)(result: Either[String, Unit]): Unit =
    (mockNSIConnector.updateEmail(_: NSIUserInfo)(_: HeaderCarrier, _: ExecutionContext))
      .expects(userInfo, *, *)
      .returning(EitherT.fromEither[Future](result))

  def checkIsErrorPage(result: Future[Result]): Unit = {
    status(result) shouldBe SEE_OTHER
    redirectLocation(result) shouldBe Some(routes.AccountHolderUpdateEmailAddressController.getEmailUpdateError().url)
  }

  "The AccountHolderUpdateEmailAddressController" when {

    "handling requests to update email addresses" must {

        def getUpdateYourEmailAddress(): Future[Result] =
          controller.getUpdateYourEmailAddress()(fakeRequestWithCSRFToken)

      behave like commonEnrolmentBehaviour(
        () ⇒ getUpdateYourEmailAddress(),
        () ⇒ mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval),
        () ⇒ mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(None))

      "show a page which allows the user to change their email if they are already " +
        "enrolled and we have an email stored for them" in {
          inSequence{
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
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

      val fakePostRequest = fakeRequestWithCSRFToken.withFormUrlEncodedBody("new-email-address" → email)

        def submit(email: String): Future[Result] =
          controller.onSubmit()(FakeRequest().withFormUrlEncodedBody("new-email-address" → email))

      behave like commonEnrolmentBehaviour(
        () ⇒ submit("email"),
        () ⇒ mockAuthWithNINOAndName(AuthWithCL200)(mockedNINOAndNameRetrieval),
        () ⇒ mockAuthWithNINOAndName(AuthWithCL200)(mockedNINOAndNameRetrievalMissingNino)
      )

      "return the check your email page with a status of Ok, given a valid email address " in {
        inSequence {
          mockAuthWithNINOAndName(AuthWithCL200)(mockedNINOAndNameRetrieval)
          mockEnrolmentCheck()(Right(enrolled))
          mockEmailGet()(Right(Some("email")))
          mockEmailVerificationConn(nino, email, firstName)(Right(()))
        }
        val result = await(controller.onSubmit()(fakePostRequest))
        status(result) shouldBe Status.OK
        contentAsString(result) should include("You have 30 minutes to verify your email address")
      }

      "return an AlreadyVerified status and redirect the user to email verified page," +
        " given an email address of an already verified user " in {
          inSequence {
            mockAuthWithNINOAndName(AuthWithCL200)(mockedNINOAndNameRetrieval)
            mockEnrolmentCheck()(Right(enrolled))
            mockEmailGet()(Right(Some("email")))
            mockEmailVerificationConn(nino, email, firstName)(Left(AlreadyVerified))
          }
          val result = await(controller.onSubmit()(fakePostRequest))(10.seconds)
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

      "show an error page if the email verification fails" in {
        val fakePostRequest = FakeRequest().withFormUrlEncodedBody("new-email-address" → email)
        inSequence {
          mockAuthWithNINOAndName(AuthWithCL200)(mockedNINOAndNameRetrieval)
          mockEnrolmentCheck()(Right(enrolled))
          mockEmailGet()(Right(Some("email")))
          mockEmailVerificationConn(nino, email, firstName)(Left(OtherError))
        }
        val result = controller.onSubmit()(fakePostRequest)
        status(result) shouldBe Status.OK
        contentAsString(result).contains("Email verification error") shouldBe true
      }

      "redirect to the error page if the name retrieval fails" in {
        mockAuthWithNINOAndName(AuthWithCL200)(mockedNINOAndNameRetrievalMissingName)

        val result = controller.onSubmit()(fakePostRequest)
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.AccountHolderUpdateEmailAddressController.getEmailUpdateError().url)
      }

    }

    "handling verified emails" must {

      val verifiedEmail = "new email"
      val emailVerificationParams = EmailVerificationParams(nino, verifiedEmail)

        def verifyEmail(params: String): Future[Result] =
          controller.emailVerified(params)(FakeRequest())

      behave like commonEnrolmentBehaviour(
        () ⇒ verifyEmail(emailVerificationParams.encode()),
        () ⇒ mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals),
        () ⇒ mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsMissingNinoEnrolment)
      )

      "show a success page if the NINO in the URL matches the NINO from auth, the update with " +
        "NS&I is successful and the email is successfully updated in mongo" in {
          inSequence{
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockEnrolmentCheck()(Right(Enrolled(true)))
            mockEmailGet()(Right(Some("email")))
            mockUpdateEmailWithNSI(nsiUserInfo.updateEmail(verifiedEmail))(Right(()))
            mockStoreEmail(verifiedEmail)(Right(()))
            mockAuditEmailChanged()
          }

          val result = verifyEmail(emailVerificationParams.encode())
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.AccountHolderUpdateEmailAddressController.getEmailUpdated(emailVerificationParams.email).url)
        }

      "redirect to NS&I" when {

        "the NINO in the URL matches the NINO from auth, the update with " +
          "NS&I is successful but the email is not successfully updated in mongo" in {
            inSequence{
              mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
              mockEnrolmentCheck()(Right(Enrolled(true)))
              mockEmailGet()(Right(Some("email")))
              mockUpdateEmailWithNSI(nsiUserInfo.updateEmail(verifiedEmail))(Right(()))
              mockStoreEmail(verifiedEmail)(Left(""))
              mockAuditEmailChanged()
            }

            val result = verifyEmail(emailVerificationParams.encode())
            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some(FrontendAppConfig.nsiManageAccountUrl)
          }

      }

      "return an error" when {

        "the parameter in the URL cannot be decoded" in {
          inSequence {
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockAuditSuspiciousActivity()
          }

          val result = verifyEmail("random crap")
          checkIsErrorPage(result)
        }

        "the NINO in the URL does not match the NINO from auth" in {
          inSequence{
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockEnrolmentCheck()(Right(Enrolled(true)))
            mockEmailGet()(Right(Some("email")))
            mockAuditSuspiciousActivity()
          }

          val result = verifyEmail(emailVerificationParams.copy(nino = "other nino").encode())
          checkIsErrorPage(result)
        }

        "there is missing user info from auth" in {
          inSequence{
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsMissingUserInfo)
            mockEnrolmentCheck()(Right(Enrolled(true)))
            mockEmailGet()(Right(Some("email")))
          }

          val result = verifyEmail(emailVerificationParams.encode())
          checkIsErrorPage(result)
        }

        "the call to NS&I to update the email is unsuccessful" in {
          inSequence{
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockEnrolmentCheck()(Right(Enrolled(true)))
            mockEmailGet()(Right(Some("email")))
            mockUpdateEmailWithNSI(nsiUserInfo.updateEmail(verifiedEmail))(Left(""))
          }

          val result = verifyEmail(emailVerificationParams.encode())
          checkIsErrorPage(result)
        }

      }
    }

    "handling getEmailUpdated" must {

      "return the email updated page" in {
        mockAuthWithNoRetrievals(AuthProvider)

        val result = controller.getEmailUpdated("email")(FakeRequest())
        status(result) shouldBe OK
        contentAsString(result) should include("Email Address Verified")
      }
    }

    "handling getEmailUpdateError" must {

      "return the email update error page" in {
        mockAuthWithNoRetrievals(AuthProvider)

        val result = controller.getEmailUpdateError(FakeRequest())
        status(result) shouldBe OK
        contentAsString(result) should include("We cannot change your email address at this time")
      }
    }
  }

  def commonEnrolmentBehaviour(getResult:          () ⇒ Future[Result],
                               mockSuccessfulAuth: () ⇒ Unit,
                               mockNoNINOAuth:     () ⇒ Unit
  ): Unit = { // scalastyle:ignore method.length

    "return an error" when {

      "the user has no NINO" in {
        mockNoNINOAuth()

        checkIsTechnicalErrorPage(getResult())
      }

      "there is an error getting the enrolment status" in {
        inSequence{
          mockSuccessfulAuth()
          mockEnrolmentCheck()(Left(""))
        }

        checkIsErrorPage(getResult())
      }

      "there is an error getting the confirmed email" in {
        inSequence{
          mockSuccessfulAuth()
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockEmailGet()(Left(""))
        }

        checkIsErrorPage(getResult())
      }

      "the user is not enrolled" in {
        inSequence{
          mockSuccessfulAuth()
          mockEnrolmentCheck()(Right(NotEnrolled))
          mockEmailGet()(Right(Some("email")))
          mockAuditSuspiciousActivity()
        }

        checkIsErrorPage(getResult())
      }

      "the user is enrolled but has no stored email" in {
        inSequence{
          mockSuccessfulAuth()
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockEmailGet()(Right(None))
          mockAuditSuspiciousActivity()
        }

        checkIsErrorPage(getResult())
      }

    }
  }

}
