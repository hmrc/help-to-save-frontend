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
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.forms.ReminderFrequencyValidation
import uk.gov.hmrc.helptosavefrontend.models.EnrolmentStatus.{Enrolled, NotEnrolled}
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithCL200
import uk.gov.hmrc.helptosavefrontend.models.TestData.Eligibility.{randomEligibility, randomEligibleWithUserInfo, randomIneligibility}
import uk.gov.hmrc.helptosavefrontend.models.TestData.UserData.validUserInfo
import uk.gov.hmrc.helptosavefrontend.models.*
import uk.gov.hmrc.helptosavefrontend.models.account.Account
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityCheckResponse
import uk.gov.hmrc.helptosavefrontend.models.reminder.{CancelHtsUserReminder, HtsUserSchedule}
import uk.gov.hmrc.helptosavefrontend.services.{HelpToSaveReminderService, HelpToSaveService}
import uk.gov.hmrc.helptosavefrontend.util.Crypto
import uk.gov.hmrc.helptosavefrontend.views.html.closeaccount.account_closed
import uk.gov.hmrc.helptosavefrontend.views.html.reminder.*

import java.time.LocalDate
import scala.concurrent.Future
import scala.util.Try

class ReminderControllerSpec
    extends ControllerSpecWithGuiceApp with AuthSupport with CSRFSupport with SessionStoreBehaviourSupport {

  override implicit val crypto: Crypto = mock[Crypto]
  implicit val reminderFrequencyValidation: ReminderFrequencyValidation = new ReminderFrequencyValidation()
  val encryptedEmail = "encrypted"
  val mockAuditor = mock[HTSAuditor]

  val mockHelpToSaveReminderService = mock[HelpToSaveReminderService]
  val mockHelpToSaveService = mock[HelpToSaveService]

  def mockUpdateHtsUserPost(htsUser: HtsUserSchedule)(result: Either[String, HtsUserSchedule]): Unit =
    when(mockHelpToSaveReminderService.updateHtsUser(eqTo(htsUser))(any(), any()))
      .thenReturn(EitherT.fromEither[Future](result))

  val mockedFeatureEnabled: Boolean = false

  def mockCancelRemindersAuditEvent(nino: String, emailAddress: String): Unit =
    doNothing()
      .when(mockAuditor)
      .sendEvent(eqTo(HtsReminderCancelledEvent(HtsReminderCancelled(nino, emailAddress), "/")), eqTo(nino))(any())

  def mockUpdateRemindersAuditEvent(user: HTSReminderAccount): Unit =
    doNothing()
      .when(mockAuditor)
      .sendEvent(eqTo(HtsReminderUpdatedEvent(HtsReminderUpdated(user), "/")), eqTo(nino))(any())

  def mockCreateRemindersAuditEvent(user: HTSReminderAccount): Unit =
    doNothing()
      .when(mockAuditor)
      .sendEvent(eqTo(HtsReminderCreatedEvent(HtsReminderCreated(user), "/")), eqTo(nino))(any())

  def mockCancelHtsUserReminderPost(cancelHtsUserReminder: CancelHtsUserReminder)(result: Either[String, Unit]): Unit =
    when(mockHelpToSaveReminderService.cancelHtsUserReminders(eqTo(cancelHtsUserReminder))(any(), any())).thenReturn(
      EitherT
        .fromEither[Future](result)
    )

  def mockGetHtsUser(nino: String)(result: Either[String, HtsUserSchedule]): Unit =
    when(mockHelpToSaveReminderService.getHtsUser(eqTo(nino))(any(), any()))
      .thenReturn(EitherT.fromEither[Future](result))

  def mockEnrolmentCheck()(result: Either[String, EnrolmentStatus]): Unit =
    when(mockHelpToSaveService.getUserEnrolmentStatus()(any(), any())).thenReturn(EitherT.fromEither[Future](result))

  def mockEmailGet()(result: Either[String, Option[String]]): Unit =
    when(mockHelpToSaveService.getConfirmedEmail()(any(), any())).thenReturn(EitherT.fromEither[Future](result))

  def mockEncrypt(p: String)(result: String): Unit = when(crypto.encrypt(p)).thenReturn(result)

  def mockGetAccount(nino: String)(result: Either[String, Account]): Unit =
    when(mockHelpToSaveService.getAccount(eqTo(nino), any())(any(), any()))
      .thenReturn(EitherT.fromEither[Future](result))

  def mockDecrypt(p: String)(result: String): Unit = when(crypto.decrypt(p)).thenReturn(Try(result))

  def newController()(implicit crypto: Crypto, reminderFrequencyValidation: ReminderFrequencyValidation) =
    new ReminderController(
      mockHelpToSaveReminderService,
      mockHelpToSaveService,
      mockSessionStore,
      mockAuthConnector,
      mockMetrics,
      mockAuditor,
      testCpd,
      testMcc,
      testErrorHandler,
      testMaintenanceSchedule,
      injector.instanceOf[email_savings_reminders],
      injector.instanceOf[reminder_frequency_set],
      injector.instanceOf[reminder_confirmation],
      injector.instanceOf[reminder_cancel_confirmation],
      injector.instanceOf[reminder_dashboard],
      injector.instanceOf[apply_savings_reminders],
      injector.instanceOf[account_closed]
    ) {}
  lazy val controller = newController()

  "The Reminder Controller" must {

    val fakeRequest = FakeRequest("POST", "/").withFormUrlEncodedBody("reminderFrequency" -> "1st")

    def unused[T](arg: T): Unit = arg match { case _ => () }

    def verifyHtsUserUpdate(params: HtsUserSchedule): Future[Result] = {
      unused(params)
      csrfAddToken(controller.selectRemindersSubmit())(fakeRequest)
    }
    def verifiedHtsUserUpdate(params: HtsUserSchedule): Future[Result] = {
      unused(params)
      csrfAddToken(controller.selectedRemindersSubmit())(fakeRequest)
    }
    def cancelHtsUserReminders(params: CancelHtsUserReminder): Future[Result] = {
      unused(params)
      csrfAddToken(controller.selectedRemindersSubmit())(fakeRequest)
    }
//    def verifyHtsUser(params: HtsUserSchedule): Future[Result] =
//      csrfAddToken(controller.submitApplySavingsReminderPage())(fakeRequest)

    "should show a success page if the user submits an HtsUser to update in the HTS Reminder backend service " in {
      val htsUserToBeUpdated = HtsUserSchedule(Nino(nino), "email", firstName, lastName, true, Seq(1), LocalDate.now())
      val hTSReminderAccount = HTSReminderAccount(
        htsUserToBeUpdated.nino.value,
        htsUserToBeUpdated.email,
        htsUserToBeUpdated.firstName,
        htsUserToBeUpdated.lastName,
        htsUserToBeUpdated.optInStatus,
        htsUserToBeUpdated.daysToReceive
      )

      mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
      mockEmailGet()(Right(Some("email")))
      mockCreateRemindersAuditEvent(hTSReminderAccount)
      mockUpdateHtsUserPost(htsUserToBeUpdated)(Right(htsUserToBeUpdated))
      mockEncrypt("email")(encryptedEmail)

      val result = verifyHtsUserUpdate(htsUserToBeUpdated)
      status(result) shouldBe Status.SEE_OTHER

    }

    "should redirect to internal server error page if the call to htsContext userdetails returns missingUserInfos  " in {
      val htsUserForUpdate = HtsUserSchedule(Nino(nino), "email", firstName, lastName, true, Seq(1), LocalDate.now())

      mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsMissingUserInfo)

      val result = verifyHtsUserUpdate(htsUserForUpdate)
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR

    }

    "should redirect to internal server error page if email retrieved is empty " in {
      val htsUserForUpdate = HtsUserSchedule(Nino(nino), "email", firstName, lastName, true, Seq(1), LocalDate.now())

      mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
      mockEmailGet()(Right(Some("")))

      val result = verifyHtsUserUpdate(htsUserForUpdate)
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR

    }

    "should redirect to internal server error page if htsUser update fails " in {
      val htsUserToBeUpdated = HtsUserSchedule(Nino(nino), "email", firstName, lastName, true, Seq(1), LocalDate.now())
      val hTSReminderAccount = HTSReminderAccount(
        htsUserToBeUpdated.nino.value,
        htsUserToBeUpdated.email,
        htsUserToBeUpdated.firstName,
        htsUserToBeUpdated.lastName,
        htsUserToBeUpdated.optInStatus,
        htsUserToBeUpdated.daysToReceive
      )

      mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
      mockEmailGet()(Right(Some("email")))
      mockCreateRemindersAuditEvent(hTSReminderAccount)
      mockUpdateHtsUserPost(htsUserToBeUpdated)(Left("error occurred while updating htsUser"))

      val result = verifyHtsUserUpdate(htsUserToBeUpdated)
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR

    }

    "should show the form validation errors when the user submits an HtsUser to update in the HTS Reminder backend service with nobody " in {

      val fakeRequestWithNoBody = FakeRequest("POST", "/").withFormUrlEncodedBody("reminderFrequency" -> "")

      mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)

      val result = csrfAddToken(controller.selectRemindersSubmit())(fakeRequestWithNoBody)
      status(result) shouldBe Status.OK

    }

    "should redirect to internal server error page if user info is missing from the htsContext " in {

      val fakeRequestWithNoBody = FakeRequest("POST", "/").withFormUrlEncodedBody("reminderFrequency" -> "1st")

      mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsMissingUserInfo)

      val result = csrfAddToken(controller.selectRemindersSubmit())(fakeRequestWithNoBody)
      status(result) shouldBe INTERNAL_SERVER_ERROR

    }

    "should return the reminder frquency setting page when asked for it" in {
      val getHtsUser = HtsUserSchedule(Nino(nino), "email", firstName, lastName, true, Seq(1), LocalDate.now())

      val fakeRequestWithNoBody = FakeRequest("GET", "/")

      mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
      mockGetHtsUser(nino)(Right(getHtsUser))

      val result = csrfAddToken(controller.getEmailsavingsReminders())(fakeRequestWithNoBody)
      status(result) shouldBe Status.OK

    }
    "should return the reminder frquency dashboard page when asked for it" in {

      val fakeRequestWithNoBody = FakeRequest("GET", "/")

      mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
      mockGetHtsUser(nino)(Left("error occurred while getting htsUser"))

      val result = csrfAddToken(controller.getEmailsavingsReminders())(fakeRequestWithNoBody)
      status(result) shouldBe Status.OK

    }

    "should return the reminder setting page when asked for it" in {
      val account = Account(false, 123.45, 0, 0, 0, LocalDate.parse("1900-01-01"), List(), None, None)

      mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(Some(nino) and enrolmentsWithMatchingNino)
      mockGetAccount(nino)(Right(account))

      val result = csrfAddToken(controller.getSelectRendersPage())(FakeRequest())
      status(result) shouldBe Status.OK
    }

    "should return acount closed page when account is closed" in {
      val account = Account(true, 123.45, 0, 0, 0, LocalDate.parse("1900-01-01"), List(), None, None)

      mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(Some(nino) and enrolmentsWithMatchingNino)
      mockGetAccount(nino)(Right(account))

      val result = csrfAddToken(controller.getSelectRendersPage())(FakeRequest())
      status(result) shouldBe Status.OK
    }

    "should return the reminder setting page when asked for " in {

      mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(Some(nino) and enrolmentsWithMatchingNino)
      mockGetAccount(nino)(Left("error occurred while getting htsUser"))

      val result = csrfAddToken(controller.getSelectRendersPage())(FakeRequest())
      status(result) shouldBe Status.OK
    }

    "should redirect to an the internal server error page if email retrieveal is failed" in {
      val htsUserForUpdate = HtsUserSchedule(Nino(nino), "email", firstName, lastName, true, Seq(1), LocalDate.now())

      mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
      mockEmailGet()(Left("error occurred while retrieving the email details"))

      val result = verifyHtsUserUpdate(htsUserForUpdate)
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR

    }

    "should redirect to a renders confirmation set page with email encrypted " in {

      mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
      mockDecrypt("encrypted")("email")
      val fakeRequestWithNoBody = FakeRequest("GET", "/")
      val result = controller.getRendersConfirmPage("encrypted", "1st", "Set")(fakeRequestWithNoBody)
      status(result) shouldBe Status.OK

    }

    "should redirect to a renders confirmation update page with email encrypted " in {

      mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
      mockDecrypt("encrypted")("email")
      val fakeRequestWithNoBody = FakeRequest("GET", "/")
      val result = controller.getRendersConfirmPage("encrypted", "1st", "Update")(fakeRequestWithNoBody)
      status(result) shouldBe Status.OK

    }

    "should return the selected reminder   page when asked for it" in {
      val account = Account(false, 123.45, 0, 0, 0, LocalDate.parse("1900-01-01"), List(), None, None)
      val fakeRequestWithNoBody = FakeRequest("GET", "/")

      mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(Some(nino) and enrolmentsWithMatchingNino)
      mockGetAccount(nino)(Right(account))
      val result = csrfAddToken(controller.getSelectedRendersPage())(fakeRequestWithNoBody)
      status(result) shouldBe Status.SEE_OTHER

    }

    "should return the account closed page when account is closed" in {
      val account = Account(true, 123.45, 0, 0, 0, LocalDate.parse("1900-01-01"), List(), None, None)
      val fakeRequestWithNoBody = FakeRequest("GET", "/")

      mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(Some(nino) and enrolmentsWithMatchingNino)
      mockGetAccount(nino)(Right(account))
      val result = csrfAddToken(controller.getSelectedRendersPage())(fakeRequestWithNoBody)
      status(result) shouldBe Status.OK

    }

    "should return the internal error when selected reminderpage " in {
      val fakeRequestWithNoBody = FakeRequest("GET", "/")

      mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
      mockGetAccount(nino)(Left("error occurred while getting htsUser"))

      val result = csrfAddToken(controller.getSelectedRendersPage())(fakeRequestWithNoBody)
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR

    }

    "should return the selected reminder  page when asked for" in {
      val getHtsUser = HtsUserSchedule(Nino(nino), "email", firstName, lastName, true, Seq(1), LocalDate.now())

      val fakeRequestWithNoBody = FakeRequest("GET", "/")

      mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
      mockGetHtsUser(nino)(Right(getHtsUser))

      val result = csrfAddToken(controller.accountOpenGetSelectedRendersPage())(fakeRequestWithNoBody)
      status(result) shouldBe Status.OK
    }

    "should return the internal error when error retrieving account " in {
      val fakeRequestWithNoBody = FakeRequest("GET", "/")
      mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
      mockGetHtsUser(nino)(Left("error occurred while getting htsUser"))
      val result = csrfAddToken(controller.accountOpenGetSelectedRendersPage())(fakeRequestWithNoBody)
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR
    }

    "should return the Cancel reminder   page when asked for it" in {

      val fakeRequestWithNoBody = FakeRequest("GET", "/")

      mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)

      val result = csrfAddToken(controller.getRendersCancelConfirmPage())(fakeRequestWithNoBody)
      status(result) shouldBe Status.OK

    }

    "should show a success page if the user submits an CancelHtsUserReminder to cancel in the HTS Reminder backend service " in {
      val ninoNew = "WM123456C"
      val cancelHtsUserReminder = CancelHtsUserReminder(ninoNew)
      val fakeRequestWithNoBody = FakeRequest("POST", "/").withFormUrlEncodedBody("reminderFrequency" -> "cancel")

      mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
      mockEmailGet()(Right(Some("email")))
      mockCancelRemindersAuditEvent(ninoNew, "email")
      mockCancelHtsUserReminderPost(cancelHtsUserReminder)(Right((())))

      val result = csrfAddToken(controller.selectedRemindersSubmit())(fakeRequestWithNoBody)
      status(result) shouldBe SEE_OTHER
    }

    "should redirect to an the internal server error page if email retrieveal is failed in selected renders submit" in {
      val htsUserForUpdate = HtsUserSchedule(Nino(nino), "email", firstName, lastName, true, Seq(1), LocalDate.now())

      mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
      mockEmailGet()(Left("error occurred while retrieving the email details"))

      val result = verifiedHtsUserUpdate(htsUserForUpdate)
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR

    }
    "should show a error page if the user submits an CancelHtsUserReminder to cancel in the HTS Reminder backend service " in {
      val ninoNew = "WM123456C"
      val cancelHtsUserReminder = CancelHtsUserReminder(ninoNew)
      val fakeRequestWithNoBody = FakeRequest("POST", "/").withFormUrlEncodedBody("reminderFrequency" -> "cancel")

      mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
      mockEmailGet()(Right(Some("email")))
      mockCancelRemindersAuditEvent(ninoNew, "email")
      mockCancelHtsUserReminderPost(cancelHtsUserReminder)(Left("error occurred while updating htsUser"))

      val result = csrfAddToken(controller.selectedRemindersSubmit())(fakeRequestWithNoBody)
      status(result) shouldBe INTERNAL_SERVER_ERROR
    }
    "should show a success page if the user submits an CancelHtsUserReminder nextpage to cancel in the HTS Reminder backend service " in {
      val ninoNew = "AE123456D"
      val cancelHtsUserReminder = CancelHtsUserReminder(ninoNew)
      val htsUserToBeUpdated = HtsUserSchedule(Nino(nino), "email", firstName, lastName, true, Seq(1), LocalDate.now())
      val hTSReminderAccount = HTSReminderAccount(
        htsUserToBeUpdated.nino.value,
        htsUserToBeUpdated.email,
        htsUserToBeUpdated.firstName,
        htsUserToBeUpdated.lastName,
        htsUserToBeUpdated.optInStatus,
        htsUserToBeUpdated.daysToReceive
      )

      mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
      mockEmailGet()(Right(Some("email")))
      mockUpdateRemindersAuditEvent(hTSReminderAccount)
      mockUpdateHtsUserPost(htsUserToBeUpdated)(Right(htsUserToBeUpdated))
      mockEncrypt("email")(encryptedEmail)

      val result = cancelHtsUserReminders(cancelHtsUserReminder)
      status(result) shouldBe Status.SEE_OTHER

    }
    "should redirect to internal server error page if htsUser update fails in selected submit " in {
      val htsUserToBeUpdated = HtsUserSchedule(Nino(nino), "email", firstName, lastName, true, Seq(1), LocalDate.now())
      val hTSReminderAccount = HTSReminderAccount(
        htsUserToBeUpdated.nino.value,
        htsUserToBeUpdated.email,
        htsUserToBeUpdated.firstName,
        htsUserToBeUpdated.lastName,
        htsUserToBeUpdated.optInStatus,
        htsUserToBeUpdated.daysToReceive
      )

      mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
      mockEmailGet()(Right(Some("email")))
      mockUpdateRemindersAuditEvent(hTSReminderAccount)
      mockUpdateHtsUserPost(htsUserToBeUpdated)(Left("error occurred while updating htsUser"))

      val result = verifiedHtsUserUpdate(htsUserToBeUpdated)
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR

    }

    "should redirect to internal server error page if user info is missing from the htsContext in selected submit" in {

      val fakeRequestWithNoBody = FakeRequest("POST", "/").withFormUrlEncodedBody("reminderFrequency" -> "1st")

      mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsMissingUserInfo)

      val result = csrfAddToken(controller.selectedRemindersSubmit())(fakeRequestWithNoBody)
      status(result) shouldBe INTERNAL_SERVER_ERROR

    }

    "should show the form validation errors when the user submits an Cancel Reminders to cancel in the HTS Reminder backend service with nobody " in {

      val fakeRequestWithNoBody = FakeRequest("POST", "/").withFormUrlEncodedBody("reminderFrequency" -> "")

      mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)

      val result = csrfAddToken(controller.selectedRemindersSubmit())(fakeRequestWithNoBody)
      status(result) shouldBe Status.OK

    }
    "should show the form validation errors when the user submits no selection in the HTS Reminder " in {

      val fakeRequestWithNoBody = FakeRequest("POST", "/").withFormUrlEncodedBody("reminderFrequency" -> "")

      mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
      mockEnrolmentCheck()(Right(Enrolled(true)))

      val result = csrfAddToken(controller.submitApplySavingsReminderPage())(fakeRequestWithNoBody)
      status(result) shouldBe Status.SEE_OTHER
    }

    "should return the apply savings reminder  page when asked for it" in {
      val fakeRequestWithNoBody = FakeRequest("GET", "/")

      mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
      mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
      mockSessionStoreGet(
        Right(
          Some(
            HTSSession(
              Some(Right(randomEligibleWithUserInfo(validUserInfo))),
              None,
              None,
              None,
              None,
              None,
              None,
              None,
              false,
              None,
              false,
              true,
              None
            )
          )
        )
      )

      val result = csrfAddToken(controller.getApplySavingsReminderPage())(fakeRequestWithNoBody)
      status(result) shouldBe Status.OK

    }
    "should return the apply savings reminder  page with out select when asked for it" in {
      val fakeRequestWithNoBody = FakeRequest("GET", "/")

      mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
      mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))

      val result = csrfAddToken(controller.getApplySavingsReminderPage())(fakeRequestWithNoBody)
      status(result) shouldBe SEE_OTHER

    }
    "should show a success page if the user submits an ApplySavingsReminderPage with No  " in {
      val fakeRequestWithNoBody = FakeRequest("POST", "/").withFormUrlEncodedBody("reminderFrequency" -> "no")

      mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
      mockEnrolmentCheck()(Right(NotEnrolled))
      mockSessionStoreGet(Right(None))

      val result = csrfAddToken(controller.submitApplySavingsReminderPage())(fakeRequestWithNoBody)
      status(result) shouldBe SEE_OTHER
    }

    "should show a success page if the user submits an ApplySavingsReminderPage with no  " in {
      val fakeRequestWithNoBody = FakeRequest("POST", "/").withFormUrlEncodedBody("reminderFrequency" -> "no")
      val eligibilityResult = Some(Right(randomEligibleWithUserInfo(validUserInfo)))
      mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
      mockEnrolmentCheck()(Right(NotEnrolled))
      mockSessionStoreGet(
        Right(
          Some(
            HTSSession(
              eligibilityResult,
              None,
              None,
              None,
              None,
              None,
              reminderDetails = Some("1st"),
              None,
              false,
              None,
              false,
              true
            )
          )
        )
      )
      mockSessionStorePut(
        HTSSession(
          eligibilityResult,
          None,
          None,
          None,
          None,
          None,
          reminderDetails = Some("none"),
          reminderValue = Some("no"),
          false,
          None,
          false,
          false
        )
      )(Right(()))

      val result = csrfAddToken(controller.submitApplySavingsReminderPage())(fakeRequestWithNoBody)
      status(result) shouldBe SEE_OTHER
    }
    "should show a success page if the user submits an ApplySavingsReminderPage with out option  " in {
      val fakeRequestWithNoBody = FakeRequest("POST", "/").withFormUrlEncodedBody("reminderFrequency" -> "yes")
      val eligibilityResult = Some(Right(randomEligibleWithUserInfo(validUserInfo)))
      mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
      mockEnrolmentCheck()(Right(NotEnrolled))
      mockSessionStoreGet(
        Right(
          Some(
            HTSSession(
              eligibilityResult,
              None,
              None,
              None,
              None,
              None,
              None
            )
          )
        )
      )
      mockSessionStorePut(
        HTSSession(
          eligibilityResult,
          None,
          None,
          None,
          None,
          None,
          None,
          reminderValue = Some("yes"),
          false,
          None,
          false,
          true
        )
      )(Right(()))

      val result = csrfAddToken(controller.submitApplySavingsReminderPage())(fakeRequestWithNoBody)
      status(result) shouldBe SEE_OTHER
    }
    "should show a success page if the user submits an ApplySavingsReminderPage with yes  " in {
      val fakeRequestWithNoBody = FakeRequest("POST", "/").withFormUrlEncodedBody("reminderFrequency" -> "")
      val eligibilityResult = Some(Right(randomEligibleWithUserInfo(validUserInfo)))
      mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
      mockEnrolmentCheck()(Right(NotEnrolled))
      mockSessionStoreGet(
        Right(
          Some(
            HTSSession(
              eligibilityResult,
              None,
              None,
              None,
              None,
              None,
              reminderDetails = Some("1st"),
              None,
              false,
              None,
              false,
              true
            )
          )
        )
      )

      val result = csrfAddToken(controller.submitApplySavingsReminderPage())(fakeRequestWithNoBody)
      status(result) shouldBe Status.OK
      contentAsString(result) should include("Select whether you want to set up email reminders")
    }
    "should return the apply savings reminder  signup page when asked for it" in {
      val fakeRequestWithNoBody = FakeRequest("GET", "/")

      mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
      mockEnrolmentCheck()(Right(Enrolled(true)))

      val result = csrfAddToken(controller.getApplySavingsReminderSignUpPage())(fakeRequestWithNoBody)
      status(result) shouldBe Status.SEE_OTHER

    }

    "should redirect to internal server error page if user info is missing from the htsContext in savings reminder  signup page" in {

      val fakeRequestWithNoBody = FakeRequest("POST", "/").withFormUrlEncodedBody("reminderFrequency" -> "1st")

      mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsMissingUserInfo)
      mockEnrolmentCheck()(Right(Enrolled(true)))

      val result = csrfAddToken(controller.submitApplySavingsReminderSignUpPage())(fakeRequestWithNoBody)
      status(result) shouldBe SEE_OTHER

    }

    "should show the form validation errors when the user submits an savings reminder  signup page in the HTS Reminder backend service with nobody " in {

      val fakeRequestWithNoBody = FakeRequest("POST", "/").withFormUrlEncodedBody("reminderFrequency" -> "")

      mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
      mockEnrolmentCheck()(Right(NotEnrolled))
      mockSessionStoreGet(Right(None))

      val result = csrfAddToken(controller.submitApplySavingsReminderSignUpPage())(fakeRequestWithNoBody)
      status(result) shouldBe Status.SEE_OTHER

    }
    "should redirect to an the internal server error page if email retrieveal is failed in savings reminder  signup page" in {
      val fakeRequestWithNoBody = FakeRequest("POST", "/").withFormUrlEncodedBody("reminderFrequency" -> "1st")

      mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
      mockEnrolmentCheck()(Left("unexpected error"))

      //  val result = verifyHtsUser(htsUserForUpdate)
      val result = csrfAddToken(controller.submitApplySavingsReminderSignUpPage())(fakeRequestWithNoBody)
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR

    }

    "should redirect to internal server error page if htsUser update fails in savings reminder  signup page " in {
      val fakeRequestWithNoBody = FakeRequest("POST", "/").withFormUrlEncodedBody("reminderFrequency" -> "1st")

      mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
      mockEnrolmentCheck()(Left("unexpected error"))

      // val result = verifyHtsUser(htsUserForUpdate)
      val result = csrfAddToken(controller.submitApplySavingsReminderSignUpPage())(fakeRequestWithNoBody)
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR

    }

    "should show a success page if the user submits an submitApplySavingsReminderSignUpPage with no  " in {
      val fakeRequestWithNoBody = FakeRequest("POST", "/").withFormUrlEncodedBody("reminderFrequency" -> "1st")
      val eligibilityResult = Some(Right(randomEligibleWithUserInfo(validUserInfo)))
      mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
      mockEnrolmentCheck()(Right(NotEnrolled))
      mockSessionStoreGet(
        Right(
          Some(
            HTSSession(
              eligibilityResult,
              None,
              None,
              None,
              None,
              None,
              reminderDetails = Some("1st"),
              None,
              false,
              None,
              false,
              true
            )
          )
        )
      )
      mockSessionStorePut(
        HTSSession(
          eligibilityResult,
          None,
          None,
          None,
          None,
          None,
          reminderDetails = Some("1st"),
          None,
          false,
          None,
          false,
          true
        )
      )(Right(()))

      val result = csrfAddToken(controller.submitApplySavingsReminderSignUpPage())(fakeRequestWithNoBody)
      status(result) shouldBe SEE_OTHER
    }
    "should show a success page if the user submits an submitApplySavingsReminderSignUpPage with out option  " in {
      val fakeRequestWithNoBody = FakeRequest("POST", "/").withFormUrlEncodedBody("reminderFrequency" -> "cancel")
      val eligibilityResult = Some(Right(randomEligibleWithUserInfo(validUserInfo)))
      mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
      mockEnrolmentCheck()(Right(NotEnrolled))
      mockSessionStoreGet(
        Right(
          Some(
            HTSSession(
              eligibilityResult,
              None,
              None,
              None,
              None,
              None,
              None
            )
          )
        )
      )
      mockSessionStorePut(
        HTSSession(
          eligibilityResult,
          None,
          None,
          None,
          None,
          None,
          reminderDetails = Some("none")
        )
      )(Right(()))

      val result = csrfAddToken(controller.submitApplySavingsReminderSignUpPage())(fakeRequestWithNoBody)
      status(result) shouldBe SEE_OTHER
    }
    "should show a success page if the user submits an submitApplySavingsReminderSignUpPage with cancel  " in {
      val fakeRequestWithNoBody = FakeRequest("POST", "/").withFormUrlEncodedBody("reminderFrequency" -> "")
      val eligibilityResult = Some(Right(randomEligibleWithUserInfo(validUserInfo)))
      mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
      mockEnrolmentCheck()(Right(NotEnrolled))
      mockSessionStoreGet(
        Right(
          Some(
            HTSSession(
              eligibilityResult,
              None,
              None,
              None,
              None,
              None,
              reminderDetails = Some("1st"),
              None,
              false,
              None,
              false,
              true
            )
          )
        )
      )

      val result = csrfAddToken(controller.submitApplySavingsReminderSignUpPage())(fakeRequestWithNoBody)
      status(result) shouldBe Status.OK
      contentAsString(result) should include("Select when you want to receive reminders")
    }

    "display the page with correct Back link when they came from SelectEmail page" in {
      val fakeRequestWithNoBody = FakeRequest("GET", "/")
      mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
      mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
      mockSessionStoreGet(
        Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, None)))
      )

      val result = csrfAddToken(controller.getApplySavingsReminderSignUpPage())(fakeRequestWithNoBody)
      status(result) shouldBe Status.OK
      contentAsString(result) should include(
        "Select when you want to receive reminders"
      )
      contentAsString(result) should include("/help-to-save/apply-savings-reminders")
    }

    "display the page with correct Back link when they came from emailVerified page" in {
      val fakeRequestWithNoBody = FakeRequest("GET", "/")
      mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
      mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
      mockSessionStoreGet(
        Right(
          Some(
            HTSSession(
              Some(Right(randomEligibleWithUserInfo(validUserInfo))),
              None,
              None,
              None,
              None,
              None,
              reminderDetails = Some("1st"),
              None,
              false,
              None,
              false,
              true
            )
          )
        )
      )

      val result = csrfAddToken(controller.getApplySavingsReminderSignUpPage())(fakeRequestWithNoBody)
      status(result) shouldBe Status.OK
      contentAsString(result) should include(
        "Select when you want to receive reminders"
      )
      contentAsString(result) should include("/help-to-save/apply-savings-reminders")
    }

    "display the page with correct Back link when they came from check details page" in {
      val fakeRequestWithNoBody = FakeRequest("GET", "/")
      mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
      mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
      mockSessionStoreGet(
        Right(
          Some(
            HTSSession(
              Some(Right(randomEligibleWithUserInfo(validUserInfo))),
              None,
              None,
              None,
              None,
              None,
              None,
              None,
              true
            )
          )
        )
      )

      val result = csrfAddToken(controller.getApplySavingsReminderPage())(fakeRequestWithNoBody)
      status(result) shouldBe Status.OK
      contentAsString(result) should include(
        "Do you want to receive email savings reminders"
      )
      contentAsString(result) should include("/help-to-save/create-account")

    }
    "redirect user to eligibility checks if there is no session found" in {
      val fakeRequestWithNoBody = FakeRequest("GET", "/")
      mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
      mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
      mockSessionStoreGet(Right(None))

      val result = csrfAddToken(controller.getApplySavingsReminderSignUpPage())(fakeRequestWithNoBody)
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility.url)
    }

    "redirect user to eligibility checks if there is a session but no eligibility result found in the session" in {
      val fakeRequestWithNoBody = FakeRequest("GET", "/")
      mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
      mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
      mockSessionStoreGet(Right(Some(HTSSession(None, None, None))))

      val result = csrfAddToken(controller.getApplySavingsReminderSignUpPage())(fakeRequestWithNoBody)
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility.url)
    }

  }

  def commonEnrolmentBehaviour(
    getResult: () => Future[Result],
    mockSuccessfulAuth: () => Unit,
    mockNoNINOAuth: () => Unit
  ): Unit =
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
        mockEmailGet()(Right(Some("email")))

        checkIsErrorPage(getResult())
      }

      "the user is enrolled but has no stored email" in {
        mockSuccessfulAuth()
        mockEnrolmentCheck()(Right(Enrolled(true)))
        mockEmailGet()(Right(None))

        checkIsErrorPage(getResult())
      }

    }

  def checkIsErrorPage(result: Future[Result]): Unit = {
    status(result) shouldBe SEE_OTHER
    redirectLocation(result) shouldBe Some(routes.EmailController.confirmEmailErrorTryLater.url)
  }

  "show user an in-eligible page if the session is found but user is not eligible" in {
    val fakeRequestWithNoBody = FakeRequest("GET", "/")
    mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
    mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
    mockSessionStoreGet(Right(Some(HTSSession(Some(Left(randomIneligibility())), None, None))))

    val result = csrfAddToken(controller.getApplySavingsReminderSignUpPage())(fakeRequestWithNoBody)
    status(result) shouldBe SEE_OTHER
  }

  "show user an error page if the session is found but user is not eligible and in-eligibility reason can't be parsed" in {
    val fakeRequestWithNoBody = FakeRequest("GET", "/")
    val eligibilityCheckResult = randomIneligibility().value.eligibilityCheckResult.copy(reasonCode = 999)
    mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
    mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
    mockSessionStoreGet(
      Right(
        Some(
          HTSSession(
            Some(
              Left(
                randomIneligibility().copy(
                  value = EligibilityCheckResponse(eligibilityCheckResult, randomEligibility().value.threshold)
                )
              )
            ),
            None,
            None
          )
        )
      )
    )

    val result = csrfAddToken(controller.getApplySavingsReminderSignUpPage())(fakeRequestWithNoBody)
    checkIsTechnicalErrorPage(result)
  }
  "should show a success create account page if the user submits an submitApplySavingsReminderSignUpPage with no  " in {
    val fakeRequestWithNoBody = FakeRequest("POST", "/").withFormUrlEncodedBody("reminderFrequency" -> "1st")
    val eligibilityResult = Some(Right(randomEligibleWithUserInfo(validUserInfo)))
    mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
    mockEnrolmentCheck()(Right(NotEnrolled))
    mockSessionStoreGet(
      Right(
        Some(
          HTSSession(
            eligibilityResult,
            None,
            None,
            None,
            None,
            None,
            reminderDetails = Some("1st"),
            None,
            true,
            None,
            false,
            true
          )
        )
      )
    )
    mockSessionStorePut(
      HTSSession(
        eligibilityResult,
        None,
        None,
        None,
        None,
        None,
        reminderDetails = Some("1st"),
        None,
        true,
        None,
        false,
        true
      )
    )(Right(()))

    val result = csrfAddToken(controller.submitApplySavingsReminderSignUpPage())(fakeRequestWithNoBody)
    status(result) shouldBe SEE_OTHER
  }

}
