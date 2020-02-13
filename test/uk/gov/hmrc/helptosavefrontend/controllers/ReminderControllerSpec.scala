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
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.forms.{BankDetails, ReminderFrequencyValidation, SortCode}
import uk.gov.hmrc.helptosavefrontend.models.EnrolmentStatus.{Enrolled, NotEnrolled}
import uk.gov.hmrc.helptosavefrontend.models.{EnrolmentStatus, HTSSession, SuspiciousActivity}
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithCL200
import uk.gov.hmrc.helptosavefrontend.models.reminder.HtsUser
import uk.gov.hmrc.helptosavefrontend.services.{HelpToSaveReminderService, HelpToSaveService}
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, Email, EmailVerificationParams, NINO}
import uk.gov.hmrc.helptosavefrontend.views.html.reminder.{reminder_confirmation, reminder_frequency_set}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditResult

import scala.concurrent.{ExecutionContext, Future}

class ReminderControllerSpec
  extends ControllerSpecWithGuiceApp
  with AuthSupport
  with CSRFSupport
  with SessionStoreBehaviourSupport {

  override implicit val crypto: Crypto = mock[Crypto]
  implicit val reminderFrequencyValidation: ReminderFrequencyValidation = mock[ReminderFrequencyValidation]
  val encryptedEmail = "encrypted"
  val mockAuditor = mock[HTSAuditor]

  private val fakeRequest = FakeRequest("POST", "/").withFormUrlEncodedBody("reminderFrequency" → "1st")

  val mockHelpToSaveReminderService = mock[HelpToSaveReminderService]
  val mockHelpToSaveService = mock[HelpToSaveService]

  def mockUpdateHtsUserPost(htsUser: HtsUser)(result: Either[String, HtsUser]): Unit =
    (mockHelpToSaveReminderService
      .updateHtsUser(_: HtsUser)(_: HeaderCarrier, _: ExecutionContext))
      .expects(htsUser, *, *)
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

  def mockStoreEmail(email: Email)(result: Either[String, Unit]): Unit =
    (mockHelpToSaveService
      .storeConfirmedEmail(_: Email)(_: HeaderCarrier, _: ExecutionContext))
      .expects(email, *, *)
      .returning(EitherT.fromEither[Future](result))

  def mockAuditSuspiciousActivity() =
    (mockAuditor
      .sendEvent(_: SuspiciousActivity, _: NINO)(_: ExecutionContext))
      .expects(*, nino, *)
      .returning(Future.successful(AuditResult.Success))

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
    injector.instanceOf[reminder_confirmation]
  ) {

  }
  lazy val controller = newController()

  /*"The ReminderController" should {

    "handling getSelectRendersPage requests" in {

        def getSelectRendersPage(): Future[Result] = csrfAddToken(controller.getSelectRendersPage)(fakeRequest)

      inSequence {
        mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(
          mockedNINORetrieval)
        mockSessionStoreGet(Right(None))
        mockEnrolmentCheck()(Left(""))
      }
      val result = getSelectRendersPage()
      status(result) shouldBe 200
      contentAsString(result) should include("Select when you want to receive reminders")
      contentAsString(result) should include("/help-to-save/account-home/reminders-frequency-set")
    }
  }*/

  "handling verified emails" must {

    val verifiedEmail = "new email"
      //val htsUserForUpdate = HtsUser(Nino("SK614700A"), "jack@mercator.it", "Jack L", true, Seq(1), LocalDate.now(), 0 )

      def verifyHtsUserUpdate(params: HtsUser): Future[Result] =
        csrfAddToken(controller.selectRemindersSubmit())(fakeRequest)

    /*behave like commonEnrolmentBehaviour(
      () ⇒
        mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals),
      () ⇒ verifyHtsUserUpdate(htsUserForUpdate),
      () ⇒
        mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(
          mockedRetrievalsMissingNinoEnrolment)
    )*/

    "show a success page if the NINO in the URL matches the NINO from auth, the update with " +
      "NS&I is successful and the email is successfully updated in mongo" in {
        val htsUserForUpdate = HtsUser(Nino(nino), "email", firstName, false, Seq(1), LocalDate.now(), 0)

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEmailGet()(Right(Some("email")))
          mockUpdateHtsUserPost(htsUserForUpdate)(Right(htsUserForUpdate))
        }

        val result = verifyHtsUserUpdate(htsUserForUpdate)
        status(result) shouldBe Status.OK
        //redirectLocation(result) shouldBe Some(
        //  routes.EmailController.confirmEmailErrorTryLater().url)
      }
  }

  def commonEnrolmentBehaviour(
      getResult:          () ⇒ Future[Result],
      mockSuccessfulAuth: () ⇒ Unit,
      mockNoNINOAuth:     () ⇒ Unit): Unit = { // scalastyle:ignore method.length

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
          mockAuditSuspiciousActivity()
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

