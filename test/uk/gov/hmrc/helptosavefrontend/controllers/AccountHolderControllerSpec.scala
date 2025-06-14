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

import cats.data.EitherT
import cats.instances.future.*
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{doNothing, when}
import play.api.http.Status
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.auth.core.syntax.retrieved.authSyntaxForRetrieved
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.connectors.EmailVerificationConnector
import uk.gov.hmrc.helptosavefrontend.models.EnrolmentStatus.{Enrolled, NotEnrolled}
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.*
import uk.gov.hmrc.helptosavefrontend.models.*
import uk.gov.hmrc.helptosavefrontend.models.account.Account
import uk.gov.hmrc.helptosavefrontend.models.email.VerifyEmailError
import uk.gov.hmrc.helptosavefrontend.models.email.VerifyEmailError.{AlreadyVerified, OtherError}
import uk.gov.hmrc.helptosavefrontend.models.userinfo.NSIPayload
import uk.gov.hmrc.helptosavefrontend.services.{HelpToSaveReminderService, HelpToSaveService}
import uk.gov.hmrc.helptosavefrontend.util.{Email, EmailVerificationParams}
import uk.gov.hmrc.helptosavefrontend.views.html.closeaccount.close_account_are_you_sure
import uk.gov.hmrc.helptosavefrontend.views.html.email.accountholder.check_your_email
import uk.gov.hmrc.helptosavefrontend.views.html.email.{update_email_address, we_updated_your_email}

import java.net.URLDecoder
import java.time.LocalDate
import scala.concurrent.Future
import scala.concurrent.duration.*
import scala.util.{Failure, Success}

class AccountHolderControllerSpec
    extends ControllerSpecWithGuiceApp with CSRFSupport with SessionStoreBehaviourSupport with AuthSupport {

  private val fakeRequest = FakeRequest("GET", "/")

  val mockHelpToSaveService = mock[HelpToSaveService]
  val mockHelpToSaveReminderService = mock[HelpToSaveReminderService]

  val mockEmailVerificationConnector = mock[EmailVerificationConnector]

  val mockAuditor = mock[HTSAuditor]

  def mockEnrolmentCheck()(result: Either[String, EnrolmentStatus]): Unit =
    when(mockHelpToSaveService.getUserEnrolmentStatus()(any(), any())).thenReturn(EitherT.fromEither[Future](result))

  def mockEmailGet()(result: Either[String, Option[String]]): Unit =
    when(mockHelpToSaveService.getConfirmedEmail()(any(), any())).thenReturn(EitherT.fromEither[Future](result))

  def mockStoreEmail(email: Email)(result: Either[String, Unit]): Unit =
    when(mockHelpToSaveService.storeConfirmedEmail(eqTo(email))(any(), any()))
      .thenReturn(EitherT.fromEither[Future](result))
  def mockUpdateRemindersEmail(result: Either[String, Unit]): Unit =
    when(mockHelpToSaveReminderService.updateReminderEmail(any())(any(), any()))
      .thenReturn(EitherT.fromEither[Future](result))

  def mockAuditSuspiciousActivity() =
    doNothing().when(mockAuditor).sendEvent(any(), eqTo(nino))(any())

  def mockAuditEmailChanged(nino: String, oldEmail: String, newEmail: String, path: String) =
    doNothing().when(mockAuditor).sendEvent(EmailChanged(nino, oldEmail, newEmail, false, path), nino)

  def mockGetAccount(nino: String)(result: Either[String, Account]): Unit =
    when(mockHelpToSaveService.getAccount(eqTo(nino), any())(any(), any()))
      .thenReturn(EitherT.fromEither[Future](result))

  lazy val controller = new AccountHolderController(
    mockHelpToSaveService,
    mockHelpToSaveReminderService,
    mockAuthConnector,
    mockEmailVerificationConnector,
    mockMetrics,
    mockAuditor,
    mockSessionStore,
    testCpd,
    testMcc,
    testErrorHandler,
    testMaintenanceSchedule,
    injector.instanceOf[update_email_address],
    injector.instanceOf[check_your_email],
    injector.instanceOf[we_updated_your_email],
    injector.instanceOf[close_account_are_you_sure]
  ) {
    override val authConnector = mockAuthConnector
  }

  def mockEmailVerificationConn(nino: String, email: String, firstName: String)(
    result: Either[VerifyEmailError, Unit]
  ) =
    when(
      mockEmailVerificationConnector.verifyEmail(eqTo(nino), eqTo(email), eqTo(firstName), eqTo(false))(any(), any())
    ).thenReturn(Future.successful(result))

  def mockUpdateEmailWithNSI(userInfo: NSIPayload)(result: Either[String, Unit]): Unit =
    when(mockHelpToSaveService.updateEmail(eqTo(userInfo))(any(), any())).thenReturn(EitherT.fromEither[Future](result))

  def checkIsErrorPage(result: Future[Result]): Unit = {
    status(result) shouldBe SEE_OTHER
    redirectLocation(result) shouldBe Some(routes.EmailController.confirmEmailErrorTryLater.url)
  }

  "The AccountHolderController" when {

    "handling requests to update email addresses" must {

      def getUpdateYourEmailAddress(): Future[Result] =
        csrfAddToken(controller.getUpdateYourEmailAddress())(fakeRequest)

      behave like commonEnrolmentBehaviour(
        () => getUpdateYourEmailAddress(),
        () => mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment),
        () => mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(None and noPersonalTaxEnrolment)
      )

      "show a page which allows the user to change their email if they are already " +
        "enrolled and we have an email stored for them" in {
        mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
        mockEnrolmentCheck()(Right(Enrolled(true)))
        mockEmailGet()(Right(Some("email")))

        val result = getUpdateYourEmailAddress()
        status(result) shouldBe OK
        contentAsString(result) should include("Change your email address")
      }
    }

    "handling form update email forms submits" must {

      val enrolled = EnrolmentStatus.Enrolled(true)

      val email = "email@test.com"

      val fakePostRequest =
        fakeRequest.withMethod("POST").withFormUrlEncodedBody("new-email-address" -> email)

      def submit(email: String): Future[Result] =
        controller.onSubmit()(
          FakeRequest()
            .withMethod("POST")
            .withFormUrlEncodedBody("new-email-address" -> email)
        )

      behave like commonEnrolmentBehaviour(
        () => submit("email"),
        () => mockAuthWithNINOAndName(AuthWithCL200)(mockedNINOAndNameRetrieval),
        () => mockAuthWithNINOAndName(AuthWithCL200)(mockedNINOAndNameRetrievalMissingNino)
      )

      "return the check your email page with a status of Ok, given a valid email address " in {
        mockAuthWithNINOAndName(AuthWithCL200)(mockedNINOAndNameRetrieval)
        mockEnrolmentCheck()(Right(enrolled))
        mockEmailGet()(Right(Some("email")))
        mockSessionStorePut(HTSSession(None, None, Some(email)))(Right(()))
        mockEmailVerificationConn(nino, email, firstName)(Right(()))

        val result = Future.successful(await(csrfAddToken(controller.onSubmit())(fakePostRequest)))
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.AccountHolderController.getCheckYourEmail.url)
      }

      "return an AlreadyVerified status and redirect the user to email verified page," +
        " given an email address of an already verified user " in {
        mockAuthWithNINOAndName(AuthWithCL200)(mockedNINOAndNameRetrieval)
        mockEnrolmentCheck()(Right(enrolled))
        mockEmailGet()(Right(Some("email")))
        mockSessionStorePut(HTSSession(None, None, Some(email)))(Right(()))
        mockEmailVerificationConn(nino, email, firstName)(Left(AlreadyVerified))

        val result = Future.successful(await(csrfAddToken(controller.onSubmit())(fakePostRequest))(60.seconds))
        status(result) shouldBe Status.SEE_OTHER

        val redirectURL = redirectLocation(result)

        redirectURL
          .getOrElse(fail("Could not find redirect location"))
          .split('=')
          .toList match {
          case _ :: param :: Nil =>
            EmailVerificationParams.decode(URLDecoder.decode(param, "UTF-8")) match {
              case Success(params) =>
                params.nino shouldBe nino
                params.email shouldBe email

              case Failure(e) =>
                fail(s"Could not decode email verification parameters string: $param", e)
            }

          case _ => fail(s"Unexpected redirect location found: $redirectURL")
        }

      }

      "show an error page if the write to session cache fails" in {
        val fakePostRequest =
          FakeRequest().withMethod("POST").withFormUrlEncodedBody("new-email-address" -> email)
        mockAuthWithNINOAndName(AuthWithCL200)(mockedNINOAndNameRetrieval)
        mockEnrolmentCheck()(Right(enrolled))
        mockEmailGet()(Right(Some("email")))
        mockSessionStorePut(HTSSession(None, None, Some(email)))(Left(""))

        val result = csrfAddToken(controller.onSubmit())(fakePostRequest)
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.EmailController.confirmEmailErrorTryLater.url)
      }

      "show an error page if the email verification fails" in {
        val fakePostRequest =
          FakeRequest().withMethod("POST").withFormUrlEncodedBody("new-email-address" -> email)
        mockAuthWithNINOAndName(AuthWithCL200)(mockedNINOAndNameRetrieval)
        mockEnrolmentCheck()(Right(enrolled))
        mockEmailGet()(Right(Some("email")))
        mockSessionStorePut(HTSSession(None, None, Some(email)))(Right(()))
        mockEmailVerificationConn(nino, email, firstName)(Left(OtherError))

        val result = csrfAddToken(controller.onSubmit())(fakePostRequest)
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.EmailController.confirmEmailErrorTryLater.url)
      }

      "redirect to the error page if the name retrieval fails" in {
        mockAuthWithNINOAndName(AuthWithCL200)(mockedNINOAndNameRetrievalMissingName)

        val result = csrfAddToken(controller.onSubmit())(fakePostRequest)
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.EmailController.confirmEmailErrorTryLater.url)
      }

    }

    "handling verified emails" must {

      val verifiedEmail = "new email"
      val emailVerificationParams = EmailVerificationParams(nino, verifiedEmail)

      def verifyEmail(params: String): Future[Result] =
        controller.emailVerifiedCallback(params)(FakeRequest())

      behave like commonEnrolmentBehaviour(
        () => verifyEmail(emailVerificationParams.encode()),
        () => mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals),
        () => mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsMissingNinoEnrolment)
      )

      "show a success page if the NINO in the URL matches the NINO from auth, the update with " +
        "NS&I is successful and the email is successfully updated in mongo" in {
        val encodedParams = emailVerificationParams.encode()

        mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
        mockEnrolmentCheck()(Right(Enrolled(true)))
        mockEmailGet()(Right(Some("email")))
        mockUpdateEmailWithNSI(nsiPayload.updateEmail(verifiedEmail))(Right(()))
        mockStoreEmail(verifiedEmail)(Right(()))
        mockUpdateRemindersEmail(Right(()))
        mockSessionStorePut(HTSSession(None, Some(verifiedEmail), None))(Right(()))
        mockAuditEmailChanged(
          nino,
          "email",
          verifiedEmail,
          routes.AccountHolderController
            .emailVerifiedCallback(encodedParams)
            .url
        )

        val result = verifyEmail(encodedParams)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.AccountHolderController.getEmailVerified.url)
      }

      "redirect to NS&I" when {

        "the NINO in the URL matches the NINO from auth, the update with " +
          "NS&I is successful but the email is not successfully updated in mongo" in {
          val encodedParams = emailVerificationParams.encode()

          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockEmailGet()(Right(Some("email")))
          mockUpdateEmailWithNSI(nsiPayload.updateEmail(verifiedEmail))(Right(()))
          mockStoreEmail(verifiedEmail)(Left(""))
          mockUpdateRemindersEmail(Right(()))
          mockAuditEmailChanged(
            nino,
            "email",
            verifiedEmail,
            routes.AccountHolderController
              .emailVerifiedCallback(encodedParams)
              .url
          )

          val result = verifyEmail(encodedParams)
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(appConfig.nsiManageAccountUrl)
        }

      }

      "return an error" when {

        "the parameter in the URL cannot be decoded" in {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockAuditSuspiciousActivity()

          val result = verifyEmail("random crap")
          checkIsErrorPage(result)
        }

        "the NINO in the URL does not match the NINO from auth" in {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockEmailGet()(Right(Some("email")))
          mockAuditSuspiciousActivity()

          val result = verifyEmail(emailVerificationParams.copy(nino = "other nino").encode())
          checkIsErrorPage(result)
        }

        "there is missing user info from auth" in {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsMissingUserInfo)
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockEmailGet()(Right(Some("email")))

          val result = verifyEmail(emailVerificationParams.encode())
          checkIsErrorPage(result)
        }

        "the call to NS&I to update the email is unsuccessful" in {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockEmailGet()(Right(Some("email")))
          mockUpdateEmailWithNSI(nsiPayload.updateEmail(verifiedEmail))(Left(""))
          mockUpdateRemindersEmail(Right(()))

          val result = verifyEmail(emailVerificationParams.encode())
          checkIsErrorPage(result)
        }

        "the write to session cache is unsuccessful" in {
          val encodedParams = emailVerificationParams.encode()

          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockEmailGet()(Right(Some("email")))
          mockUpdateEmailWithNSI(nsiPayload.updateEmail(verifiedEmail))(Right(()))
          mockStoreEmail(verifiedEmail)(Right(()))
          mockUpdateRemindersEmail(Right(()))
          mockSessionStorePut(HTSSession(None, Some(verifiedEmail), None))(Left(""))
          mockAuditEmailChanged(
            nino,
            "email",
            verifiedEmail,
            routes.AccountHolderController
              .emailVerifiedCallback(encodedParams)
              .url
          )

          val result = verifyEmail(encodedParams)
          checkIsErrorPage(result)
        }

      }
    }

    "handling getEmailVerified" must {

      "return the email verified page" in {
        mockAuthWithNoRetrievals(AuthProvider)
        mockSessionStoreGet(Right(Some(HTSSession(None, Some("email"), None))))

        val result = controller.getEmailVerified(FakeRequest())
        status(result) shouldBe OK
        contentAsString(result) should include("Email address verified")
      }

      "return an error" when {

        "there is no session" in {
          mockAuthWithNoRetrievals(AuthProvider)
          mockSessionStoreGet(Right(None))

          val result = controller.getEmailVerified(FakeRequest())
          checkIsErrorPage(result)
        }

        "there is no confirmed email in the session" in {
          mockAuthWithNoRetrievals(AuthProvider)
          mockSessionStoreGet(Right(Some(HTSSession(None, None, None))))

          val result = controller.getEmailVerified(FakeRequest())
          checkIsErrorPage(result)
        }

        "the call to session cache fails" in {
          mockAuthWithNoRetrievals(AuthProvider)
          mockSessionStoreGet(Left(""))

          val result = controller.getEmailVerified(FakeRequest())
          checkIsErrorPage(result)
        }

      }
    }

    "handling getCheckYourEmail" must {

      "return the check your email page" in {
        mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
        mockSessionStoreGet(Right(Some(HTSSession(None, None, Some("email")))))

        val result = csrfAddToken(controller.getCheckYourEmail)(fakeRequest)
        status(result) shouldBe OK
        contentAsString(result) should include("You have 30 minutes to confirm the email address")
      }

      "return an error" when {

        "there is no session" in {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
          mockSessionStoreGet(Right(None))

          val result = controller.getCheckYourEmail(FakeRequest())
          checkIsErrorPage(result)
        }

        "there is no pending email in the session" in {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
          mockSessionStoreGet(Right(Some(HTSSession(None, None, None))))

          val result = controller.getCheckYourEmail(FakeRequest())
          checkIsErrorPage(result)
        }

        "the call to session cache fails" in {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
          mockSessionStoreGet(Left(""))

          val result = controller.getCheckYourEmail(FakeRequest())
          checkIsErrorPage(result)
        }

      }
    }

  }

  "handling getCloseAccountPage" must {

    val account = Account(false, 123.45, 0, 0, 0, LocalDate.parse("1900-01-01"), List(), None, None)

    "return the close account are you sure page if they have a help-to-save account" in {
      mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(Some(nino) and enrolmentsWithMatchingNino)
      mockEnrolmentCheck()(Right(Enrolled(true)))
      mockGetAccount(nino)(Right(account))

      val result = csrfAddToken(controller.getCloseAccountPage)(fakeRequest)
      status(result) shouldBe 200
      contentAsString(result) should include("Are you sure you want to close your account?")
    }

    "redirect to NS&I if the account is already closed" in {
      mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(Some(nino) and enrolmentsWithMatchingNino)
      mockEnrolmentCheck()(Right(Enrolled(true)))
      mockGetAccount(nino)(Right(account.copy(isClosed = true)))

      val result = csrfAddToken(controller.getCloseAccountPage)(fakeRequest)
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(appConfig.nsiManageAccountUrl)
    }

    "return close account page with no account if there is any error during retrieving Account from NS&I" in {
      mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(Some(nino) and enrolmentsWithMatchingNino)
      mockEnrolmentCheck()(Right(Enrolled(true)))
      mockGetAccount(nino)(Left("unknown error"))

      val result = csrfAddToken(controller.getCloseAccountPage)(fakeRequest)
      status(result) shouldBe 200
      contentAsString(result) should include(
        "If you close your account now you will not get any bonus payments. You will not be able to open another Help to Save account."
      )
    }

    "redirect the user to the no account page if they are not enrolled in help-to-save" in {
      mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(Some(nino) and enrolmentsWithMatchingNino)
      mockEnrolmentCheck()(Right(NotEnrolled))

      val result = csrfAddToken(controller.getCloseAccountPage)(fakeRequest)
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.AccessAccountController.getNoAccountPage.url)
    }

    "throw an Internal Server Error if the enrolment check fails" in {
      mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(Some(nino) and enrolmentsWithMatchingNino)
      mockEnrolmentCheck()(Left("An error occurred"))

      val result = csrfAddToken(controller.getCloseAccountPage)(fakeRequest)
      status(result) shouldBe 500
    }

  }

  def commonEnrolmentBehaviour(
    getResult: () => Future[Result],
    mockSuccessfulAuth: () => Unit,
    mockNoNINOAuth: () => Unit
  ): Unit = // scalastyle:ignore method.length
    "return an error" when {

      "the user has no NINO" in {
        mockNoNINOAuth()

        checkIsTechnicalErrorPage(getResult())
      }

      "there is an error getting the enrolment status" in {
        mockSuccessfulAuth()
        mockEnrolmentCheck()(Left(""))

        checkIsErrorPage(getResult())
      }

      "there is an error getting the confirmed email" in {
        mockSuccessfulAuth()
        mockEnrolmentCheck()(Right(Enrolled(true)))
        mockEmailGet()(Left(""))

        checkIsErrorPage(getResult())
      }

      "the user is not enrolled" in {
        mockSuccessfulAuth()
        mockEnrolmentCheck()(Right(NotEnrolled))
        mockEmailGet()(Right(Some("email")))
        mockAuditSuspiciousActivity()

        checkIsErrorPage(getResult())
      }

      "the user is enrolled but has no stored email" in {
        mockSuccessfulAuth()
        mockEnrolmentCheck()(Right(Enrolled(true)))
        mockEmailGet()(Right(None))
        mockAuditSuspiciousActivity()

        checkIsErrorPage(getResult())
      }

    }

}
