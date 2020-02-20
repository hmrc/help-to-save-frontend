/*
 * Copyright 2020 HM Revenue & Customs
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
import java.util.Base64

import play.api.test.FakeRequest
import cats.data.EitherT
import cats.instances.future._
import org.scalamock.scalatest.MockFactory
import play.api.http.Status
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthorisationException.fromString
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.forms.{BankDetails, ReminderFrequencyValidation, SortCode}
import uk.gov.hmrc.helptosavefrontend.models.EnrolmentStatus.{Enrolled, NotEnrolled}
import uk.gov.hmrc.helptosavefrontend.models.{EnrolmentStatus, HTSSession, SuspiciousActivity}
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithCL200
import uk.gov.hmrc.helptosavefrontend.models.reminder.{CancelHtsUserReminder, HtsUser}
import uk.gov.hmrc.helptosavefrontend.services.{HelpToSaveReminderService, HelpToSaveService}
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, Email, EmailVerificationParams, NINO}
import uk.gov.hmrc.helptosavefrontend.views.html.reminder.{reminder_cancel_confirmation, reminder_confirmation, reminder_dashboard, reminder_frequency_change, reminder_frequency_set}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Try}

class ReminderControllerSpec
  extends ControllerSpecWithGuiceApp
  with AuthSupport
  with CSRFSupport
  with SessionStoreBehaviourSupport {

  override implicit val crypto: Crypto = mock[Crypto]
  implicit val reminderFrequencyValidation: ReminderFrequencyValidation = mock[ReminderFrequencyValidation]
  val encryptedEmail = "encrypted"
  val mockAuditor = mock[HTSAuditor]

  val mockHelpToSaveReminderService = mock[HelpToSaveReminderService]
  val mockHelpToSaveService = mock[HelpToSaveService]

  def mockUpdateHtsUserPost(htsUser: HtsUser)(result: Either[String, HtsUser]): Unit =
    (mockHelpToSaveReminderService
      .updateHtsUser(_: HtsUser)(_: HeaderCarrier, _: ExecutionContext))
      .expects(htsUser, *, *)
      .returning(EitherT.fromEither[Future](result))

  def mockCancelHtsUserReminderPost(cancelHtsUserReminder:CancelHtsUserReminder)(result: Either[String, Unit]): Unit =
    (mockHelpToSaveReminderService
      .cancelHtsUserReminders(_: CancelHtsUserReminder)(_: HeaderCarrier, _: ExecutionContext))
      .expects(cancelHtsUserReminder, *, *)
      .returning(EitherT.fromEither[Future](result))


  def mockGetHtsUser(nino:String) (result:Either[String, HtsUser]): Unit =
    (mockHelpToSaveReminderService
      .getHtsUser(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nino, *, *)
      .returning(EitherT.fromEither[Future](result))

  def mockEnrolmentCheck()(result: Either[String, EnrolmentStatus]): Unit =
    (mockHelpToSaveService
      .getUserEnrolmentStatus()(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *)
      .returning(EitherT.fromEither[Future](result))

  def mockEmailGet()(result: Either[String, Option[String]]): Unit =
    (mockHelpToSaveService
      .getConfirmedEmail()(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *)
      .returning(EitherT.fromEither[Future](result))

  def mockEncrypt(p: String)(result: String): Unit =
    (crypto.encrypt(_: String)).expects(p).returning(result)

  def mockDecrypt(p: String)(result: String): Unit =
    (crypto.decrypt(_: String)).expects(p).returning(Try(result))

  def newController()(implicit crypto: Crypto, reminderFrequencyValidation: ReminderFrequencyValidation) = new ReminderController(
    mockHelpToSaveReminderService,
    mockHelpToSaveService,
    mockSessionStore,
    mockAuthConnector,
    mockMetrics,
    mockAuditor,
    testCpd,
    testMcc,
    testErrorHandler,
    injector.instanceOf[reminder_frequency_set],
    injector.instanceOf[reminder_frequency_change],
    injector.instanceOf[reminder_confirmation],
    injector.instanceOf[reminder_cancel_confirmation],
    injector.instanceOf[reminder_dashboard]
  ) {

  }
  lazy val controller = newController()

  "The Reminder Controller" must {

    val fakeRequest = FakeRequest("POST", "/").withFormUrlEncodedBody("reminderFrequency" → "1st")

      def verifyHtsUserUpdate(params: HtsUser): Future[Result] =
        csrfAddToken(controller.selectRemindersSubmit())(fakeRequest)
    def verifiedHtsUserUpdate(params: HtsUser): Future[Result] =
      csrfAddToken(controller.selectedRemindersSubmit())(fakeRequest)
    def cancelHtsUserReminders(params: CancelHtsUserReminder): Future[Result] =
      csrfAddToken(controller.selectedRemindersSubmit())(fakeRequest)


    "should show a success page if the user submits an HtsUser to update in the HTS Reminder backend service " in {
      val htsUserForUpdate = HtsUser(Nino(nino), "email", firstName, true, Seq(1), LocalDate.now(), 0)

      inSequence {
        mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
        mockEmailGet()(Right(Some("email")))
        mockUpdateHtsUserPost(htsUserForUpdate)(Right(htsUserForUpdate))
        mockEncrypt("email")(encryptedEmail)

      }

      val result = verifyHtsUserUpdate(htsUserForUpdate)
      status(result) shouldBe Status.SEE_OTHER

      //redirectLocation(result) shouldBe Some(
      //  routes.EmailController.confirmEmailErrorTryLater().url)
    }

    "should redirect to internal server error page if htsUser update fails " in {
      val htsUserForUpdate = HtsUser(Nino(nino), "email", firstName, true, Seq(1), LocalDate.now(), 0)

      inSequence {
        mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
        mockEmailGet()(Right(Some("email")))
        mockUpdateHtsUserPost(htsUserForUpdate)(Left("error occurred while updating htsUser"))

      }

      val result = verifyHtsUserUpdate(htsUserForUpdate)
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR


    }

    "should show the form validation errors when the user submits an HtsUser to update in the HTS Reminder backend service with nobody " in {
      val htsUserForUpdate = HtsUser(Nino(nino), "email", firstName, false, Seq(1), LocalDate.now(), 0)

      val fakeRequestWithNoBody = FakeRequest("POST", "/").withFormUrlEncodedBody("reminderFrequency" → "")

      inSequence {
        mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
      }

      val result = csrfAddToken(controller.selectRemindersSubmit())(fakeRequestWithNoBody)
      status(result) shouldBe Status.OK

    }

    "should redirect to internal server error page if user info is missing from the htsContext " in {
      val htsUserForUpdate = HtsUser(Nino(nino), "email", firstName, false, Seq(1), LocalDate.now(), 0)

      val fakeRequestWithNoBody = FakeRequest("POST", "/").withFormUrlEncodedBody("reminderFrequency" → "1st")

      inSequence {
        mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsMissingUserInfo)
      }

      val result = csrfAddToken(controller.selectRemindersSubmit())(fakeRequestWithNoBody)
      status(result) shouldBe INTERNAL_SERVER_ERROR

    }

    "should return the reminder frquency setting page when asked for it" in {
      val getHtsUser = HtsUser(Nino(nino), "email", firstName, false, Seq(1), LocalDate.now(), 0)

      val fakeRequestWithNoBody = FakeRequest("GET", "/")

      inSequence {
        mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(
          mockedNINORetrieval)
         mockGetHtsUser(nino)(Right(getHtsUser))
      }

      val result = csrfAddToken(controller.getSelectRendersPage())(fakeRequestWithNoBody)
      status(result) shouldBe Status.OK

    }

    "should return the reminder frquency dashboard page when asked for it" in {
      val getHtsUser = HtsUser(Nino(nino), "email", firstName, false, Seq(1), LocalDate.now(), 0)

      val fakeRequestWithNoBody = FakeRequest("GET", "/")

      inSequence {
        mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(
          mockedNINORetrieval)
        mockGetHtsUser(nino)(Left("error occurred while getting htsUser"))
      }

      val result = csrfAddToken(controller.getSelectRendersPage())(fakeRequestWithNoBody)
      status(result) shouldBe Status.OK

    }

    "should redirect to an the internal server error page if email retrieveal is failed" in {
      val htsUserForUpdate = HtsUser(Nino(nino), "email", firstName, false, Seq(1), LocalDate.now(), 0)

      inSequence {
        mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
        mockEmailGet()(Left("error occurred while retrieving the email details"))

      }

      val result = verifyHtsUserUpdate(htsUserForUpdate)
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR

    }

    "should redirect to a renders confirmation page with email encrypted " in {

      inSequence {
        mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(
          mockedNINORetrieval)
        mockDecrypt("encrypted")("email")
      }
      val fakeRequestWithNoBody = FakeRequest("GET", "/")
      val result = controller.getRendersConfirmPage("encrypted", "1st")(fakeRequestWithNoBody)
      status(result) shouldBe Status.OK

    }

    "should return the selected reminder   page when asked for it" in {
      val getHtsUser = HtsUser(Nino(nino), "email", firstName, false, Seq(1), LocalDate.now(), 0)

      val fakeRequestWithNoBody = FakeRequest("GET", "/")

      inSequence {
        mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(
          mockedNINORetrieval)
       mockGetHtsUser(nino)(Right(getHtsUser))
      }

      val result = csrfAddToken(controller.getSelectedRendersPage())(fakeRequestWithNoBody)
      status(result) shouldBe Status.OK

    }
    "should return the internal error when selected reminderpage " in {
      val getHtsUser = HtsUser(Nino(nino), "email", firstName, false, Seq(1), LocalDate.now(), 0)

      val fakeRequestWithNoBody = FakeRequest("GET", "/")

      inSequence {
        mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(
          mockedNINORetrieval)
        mockGetHtsUser(nino)(Left("error occurred while getting htsUser"))
      }

      val result = csrfAddToken(controller.getSelectedRendersPage())(fakeRequestWithNoBody)
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR

    }

    "should return the Cancel reminder   page when asked for it" in {

      val fakeRequestWithNoBody = FakeRequest("GET", "/")

      inSequence {
        mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(
          mockedNINORetrieval)
        }

      val result = csrfAddToken(controller.getRendersCancelConfirmPage())(fakeRequestWithNoBody)
      status(result) shouldBe Status.OK

    }

    "should show a success page if the user submits an CancelHtsUserReminder to cancel in the HTS Reminder backend service " in {
      val ninoNew="WM123456C"
      val cancelHtsUserReminder = CancelHtsUserReminder(ninoNew)
      val fakeRequestWithNoBody = FakeRequest("POST", "/").withFormUrlEncodedBody("reminderFrequency" → "cancel")

      inSequence {
        mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
        mockEmailGet()(Right(Some("email")))
         mockCancelHtsUserReminderPost(cancelHtsUserReminder)(Right((())))

      }

      val result = csrfAddToken(controller.selectedRemindersSubmit())(fakeRequestWithNoBody)
      status(result) shouldBe SEE_OTHER
   }

    "should redirect to an the internal server error page if email retrieveal is failed in selected renders submit" in {
      val htsUserForUpdate = HtsUser(Nino(nino), "email", firstName, false, Seq(1), LocalDate.now(), 0)

      inSequence {
        mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
        mockEmailGet()(Left("error occurred while retrieving the email details"))

      }

      val result = verifiedHtsUserUpdate(htsUserForUpdate)
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR

    }
    "should show a error page if the user submits an CancelHtsUserReminder to cancel in the HTS Reminder backend service " in {
      val ninoNew="WM123456C"
      val cancelHtsUserReminder = CancelHtsUserReminder(ninoNew)
      val fakeRequestWithNoBody = FakeRequest("POST", "/").withFormUrlEncodedBody("reminderFrequency" → "cancel")

      inSequence {
        mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
        mockEmailGet()(Right(Some("email")))
        mockCancelHtsUserReminderPost(cancelHtsUserReminder)(Left("error occurred while updating htsUser"))

      }

      val result = csrfAddToken(controller.selectedRemindersSubmit())(fakeRequestWithNoBody)
      status(result) shouldBe INTERNAL_SERVER_ERROR
    }
    "should show a success page if the user submits an CancelHtsUserReminder nextpage to cancel in the HTS Reminder backend service " in {
      val ninoNew="AE123456D"
      val cancelHtsUserReminder = CancelHtsUserReminder(ninoNew)
      val htsUserForUpdate = HtsUser(Nino(nino), "email", firstName, true, Seq(1), LocalDate.now(), 0)


      inSequence {
        mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
        mockEmailGet()(Right(Some("email")))
        mockUpdateHtsUserPost(htsUserForUpdate)(Right(htsUserForUpdate))
        mockEncrypt("email")(encryptedEmail)
      }

      val result = cancelHtsUserReminders(cancelHtsUserReminder)
      status(result) shouldBe Status.SEE_OTHER


    }
    "should redirect to internal server error page if htsUser update fails in selected submit " in {
      val htsUserForUpdate = HtsUser(Nino(nino), "email", firstName, true, Seq(1), LocalDate.now(), 0)

      inSequence {
        mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
        mockEmailGet()(Right(Some("email")))
        mockUpdateHtsUserPost(htsUserForUpdate)(Left("error occurred while updating htsUser"))

      }

      val result = verifiedHtsUserUpdate(htsUserForUpdate)
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR


    }

    "should redirect to internal server error page if user info is missing from the htsContext in selected submit" in {

      val fakeRequestWithNoBody = FakeRequest("POST", "/").withFormUrlEncodedBody("reminderFrequency" → "1st")

      inSequence {
        mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsMissingUserInfo)
      }

      val result = csrfAddToken(controller.selectedRemindersSubmit())(fakeRequestWithNoBody)
      status(result) shouldBe INTERNAL_SERVER_ERROR

    }



    "should show the form validation errors when the user submits an Cancel Reminders to cancel in the HTS Reminder backend service with nobody " in {

      val fakeRequestWithNoBody = FakeRequest("POST", "/").withFormUrlEncodedBody("reminderFrequency" → "")

      inSequence {
        mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
      }

      val result = csrfAddToken(controller.selectedRemindersSubmit())(fakeRequestWithNoBody)
      status(result) shouldBe Status.OK

    }


  }

  def commonEnrolmentBehaviour(
      getResult:          () ⇒ Future[Result],
      mockSuccessfulAuth: () ⇒ Unit,
      mockNoNINOAuth:     () ⇒ Unit): Unit = {

    "return an error" when {

      "the user has no NINO" in {
        mockNoNINOAuth()

        checkIsTechnicalErrorPage(getResult())
      }

      "there is an error getting the enrolment status" in {
        inSequence {
          mockSuccessfulAuth()
          mockEnrolmentCheck()(Left(""))
        }

        checkIsErrorPage(getResult())
      }

      "there is an error getting the confirmed email" in {
        inSequence {
          mockSuccessfulAuth()
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockEmailGet()(Left(""))
        }

        checkIsErrorPage(getResult())
      }

      "the user is not enrolled" in {
        inSequence {
          mockSuccessfulAuth()
          mockEmailGet()(Right(Some("email")))
        }

        checkIsErrorPage(getResult())
      }

      "the user is enrolled but has no stored email" in {
        inSequence {
          mockSuccessfulAuth()
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockEmailGet()(Right(None))
        }

        checkIsErrorPage(getResult())
      }

    }
  }

  def checkIsErrorPage(result: Future[Result]): Unit = {
    status(result) shouldBe SEE_OTHER
    redirectLocation(result) shouldBe Some(
      routes.EmailController.confirmEmailErrorTryLater().url)
  }

}

