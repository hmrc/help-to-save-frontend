/*
 * Copyright 2021 HM Revenue & Customs
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

import java.util.Base64

import cats.data.EitherT
import cats.instances.future._
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.connectors.EmailVerificationConnector
import uk.gov.hmrc.helptosavefrontend.controllers.EmailControllerSpec.EligibleWithUserInfoOps
import uk.gov.hmrc.helptosavefrontend.forms.{BankDetails, SortCode}
import uk.gov.hmrc.helptosavefrontend.models.EnrolmentStatus.{Enrolled, NotEnrolled}
import uk.gov.hmrc.helptosavefrontend.models.HTSSession.EligibleWithUserInfo
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithCL200
import uk.gov.hmrc.helptosavefrontend.models.TestData.Eligibility._
import uk.gov.hmrc.helptosavefrontend.models.TestData.UserData.validUserInfo
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityCheckResultType
import uk.gov.hmrc.helptosavefrontend.models.email.VerifyEmailError
import uk.gov.hmrc.helptosavefrontend.models.email.VerifyEmailError.AlreadyVerified
import uk.gov.hmrc.helptosavefrontend.models.reminder.UpdateReminderEmail
import uk.gov.hmrc.helptosavefrontend.models.userinfo.NSIPayload
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, NINO}
import uk.gov.hmrc.helptosavefrontend.views.html.email._
import uk.gov.hmrc.helptosavefrontend.views.html.link_expired
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditResult

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class EmailControllerSpec
    extends ControllerSpecWithGuiceApp with AuthSupport with CSRFSupport with EnrolmentAndEligibilityCheckBehaviour
    with SessionStoreBehaviourSupport {

  val mockEmailVerificationConnector: EmailVerificationConnector = mock[EmailVerificationConnector]

  val mockAuditor = mock[HTSAuditor]

  override implicit val crypto: Crypto = mock[Crypto]
  val encryptedEmail = "encrypted"

  private val fakeRequest = FakeRequest("GET", "/")

  def newController()(implicit crypto: Crypto) =
    new EmailController(
      mockHelpToSaveService,
      mockHelpToSaveReminderService,
      mockSessionStore,
      mockEmailVerificationConnector,
      mockAuthConnector,
      mockMetrics,
      mockAuditor,
      testCpd,
      testMcc,
      testErrorHandler,
      testMaintenanceSchedule,
      injector.instanceOf[select_email],
      injector.instanceOf[give_email],
      injector.instanceOf[check_your_email],
      injector.instanceOf[cannot_change_email],
      injector.instanceOf[cannot_change_email_try_later],
      injector.instanceOf[link_expired],
      injector.instanceOf[email_updated]
    ) {
      override val authConnector = mockAuthConnector
    }

  lazy val controller = newController()

  val eligibleWithValidUserInfo = randomEligibleWithUserInfo(validUserInfo)

  def mockEmailVerification(nino: String, email: String, firstName: String)(result: Either[VerifyEmailError, Unit]) =
    (mockEmailVerificationConnector
      .verifyEmail(_: String, _: String, _: String, _: Boolean)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nino, email, firstName, true, *, *)
      .returning(Future.successful(result))

  def mockAudit(expectedEvent: HTSEvent) =
    (mockAuditor
      .sendEvent(_: HTSEvent, _: NINO)(_: ExecutionContext))
      .expects(expectedEvent, *, *)
      .returning(Future.successful(AuditResult.Success))

  def mockStoreConfirmedEmail(email: String)(result: Either[String, Unit]): Unit =
    (mockHelpToSaveService
      .storeConfirmedEmail(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(email, *, *)
      .returning(EitherT.fromEither[Future](result))

  def mockStoreConfirmedEmailInReminders(updateReminderEmail: UpdateReminderEmail)(result: Either[String, Unit]): Unit =
    (mockHelpToSaveReminderService
      .updateReminderEmail(_: UpdateReminderEmail)(_: HeaderCarrier, _: ExecutionContext))
      .expects(updateReminderEmail, *, *)
      .returning(EitherT.fromEither[Future](result))

  def mockGetConfirmedEmail()(result: Either[String, Option[String]]): Unit =
    (mockHelpToSaveService
      .getConfirmedEmail()(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *)
      .returning(EitherT.fromEither[Future](result))

  def mockEligibilityResult()(result: Either[String, EligibilityCheckResultType]): Unit =
    (mockHelpToSaveService
      .checkEligibility()(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *)
      .returning(EitherT.fromEither[Future](result))

  def mockUpdateEmail(nsiPayload: NSIPayload)(result: Either[String, Unit]): Unit =
    (mockHelpToSaveService
      .updateEmail(_: NSIPayload)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nsiPayload, *, *)
      .returning(EitherT.fromEither[Future](result))

  def mockEncrypt(p: String)(result: String): Unit =
    (crypto.encrypt(_: String)).expects(p).returning(result)

  def mockDecrypt(p: String)(result: String): Unit =
    (crypto.decrypt(_: String)).expects(p).returning(Try(result))

  "The EmailController" when {

    val version = appConfig.version
    val systemId = appConfig.systemId

    val testEmail = "email@gmail.com"

    val nsiAccountHomeURL = "http://localhost:7007/help-to-save-test-admin-frontend/dummy-pages/account-homepage"

    "handling getSelectEmailPage requests" must {

      def getSelectEmailPage(): Future[Result] = csrfAddToken(controller.getSelectEmailPage)(fakeRequest)

      "handle Digital(new applicant) users with an existing valid email from GG but not gone through eligibility checks" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(Right(None))
          mockEnrolmentCheck()(Right(NotEnrolled))
        }

        val result = getSelectEmailPage()
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility.url)
      }

      "handle Digital(new applicant) users with an existing valid email from GG and already gone through eligibility checks" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(
            Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, None)))
          )
          mockEnrolmentCheck()(Right(NotEnrolled))
        }

        val result = getSelectEmailPage()
        status(result) shouldBe 200
        contentAsString(result) should include("What email address do you want to use for your Help to Save account?")
      }

      "handle Digital(new applicant) users with an existing INVALID email from GG and should display giveEmailPage" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(
            Right(
              Some(
                HTSSession(
                  Some(Right(randomEligibleWithUserInfo(validUserInfo).withEmail(Some("invalidEmail")))),
                  None,
                  None
                )
              )
            )
          )
          mockEnrolmentCheck()(Right(NotEnrolled))
        }

        val result = getSelectEmailPage()
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.getGiveEmailPage.url)
      }

      "use correct back link for digital applicants when they come from check details page" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
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
          mockEnrolmentCheck()(Right(NotEnrolled))
        }

        val result = getSelectEmailPage()
        status(result) shouldBe 200
        contentAsString(result) should include("What email address do you want to use for your Help to Save account?")
        contentAsString(result) should include("/help-to-save/create-account")
      }

      "handle DE users with an existing valid email from GG" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(Right(Some(HTSSession(None, None, None))))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
          mockSessionStorePut(HTSSession(None, None, Some("tyrion_lannister@gmail.com"), None, None))(Right(None))
        }

        val result = getSelectEmailPage()
        status(result) shouldBe 200
        contentAsString(result) should include("What email address do you want to use for your Help to Save account?")
      }

      "DE users should not contain any Back link" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(Right(Some(HTSSession(None, None, None))))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
          mockSessionStorePut(HTSSession(None, None, Some("tyrion_lannister@gmail.com")))(Right(None))
        }

        val result = getSelectEmailPage()
        status(result) shouldBe 200
        contentAsString(result) should include("What email address do you want to use for your Help to Save account?")
      }

      "handle DE users with an existing INVALID email from GG" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsWithEmail(Some("invalidEmail")))
          mockSessionStoreGet(Right(Some(HTSSession(None, None, None))))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
          mockSessionStorePut(HTSSession(None, None, None, None, None))(Right(None))
        }

        val result = getSelectEmailPage()
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.getGiveEmailPage.url)
      }

      "handle DE users with NO email from GG" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsWithEmail(None))
          mockSessionStoreGet(Right(Some(HTSSession(None, None, None))))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
        }

        val result = getSelectEmailPage()
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.getGiveEmailPage.url)
      }

      "handle DE users with an Missing UserInfo from Auth" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsMissingUserInfo)
          mockSessionStoreGet(Right(Some(HTSSession(None, None, None))))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
        }

        val result = getSelectEmailPage()
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.getGiveEmailPage.url)
      }

      "handle unexpected errors during enrolment check" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(Right(Some(HTSSession(None, None, None))))
          mockEnrolmentCheck()(Left("unexpected error"))
        }

        val result = getSelectEmailPage()
        status(result) shouldBe 500
      }

      "redirect to NS&I if request comes from already enrolled Digital users with valid email in mongo" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(Right(Some(HTSSession(None, None, None))))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(Some(testEmail)))
        }

        val result = getSelectEmailPage()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(nsiAccountHomeURL)
      }
    }

    "handling selectEmailSubmit requests" must {

      def selectEmailSubmit(newEmail: Option[String]): Future[Result] =
        newEmail.fold(
          csrfAddToken(controller.selectEmailSubmit())(fakeRequest.withFormUrlEncodedBody("email" → "Yes"))
        ) { e ⇒
          csrfAddToken(controller.selectEmailSubmit())(
            fakeRequest.withFormUrlEncodedBody("email" → "No", "new-email" → e)
          )
        }

      "handle Digital(new applicant) users with no valid session in mongo" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(Right(None))
          mockEnrolmentCheck()(Right(NotEnrolled))
        }

        val result = selectEmailSubmit(None)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility.url)
      }

      "handle errors during session cache lookup in mongo" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(Left("unexpected error"))
        }

        val result = selectEmailSubmit(None)
        status(result) shouldBe 500
      }

      "handle Digital(new applicant) who submitted form with no new-email but with checked existing email" in {
        val session = HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, None)
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(Right(Some(session)))
          mockEnrolmentCheck()(Right(NotEnrolled))
          mockEncrypt(emailStr)(encryptedEmail)
          mockSessionStorePut(session.copy(hasSelectedEmail = true))(Right(()))
        }

        val result = selectEmailSubmit(None)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some("/help-to-save/confirm-email/encrypted")
      }

      "handle Digital(new applicant) who submitted form with new-email" in {

        val userInfo = randomEligibleWithUserInfo(validUserInfo)
        val session = HTSSession(
          Some(Right(userInfo)),
          None,
          None,
          None,
          None,
          Some(BankDetails(SortCode(1, 2, 3, 4, 5, 6), "1", None, "name"))
        )

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(Right(Some(session)))
          mockEnrolmentCheck()(Right(NotEnrolled))
          mockSessionStorePut(session.copy(pendingEmail = Some(testEmail), hasSelectedEmail = true))(Right(None))
        }

        val result = selectEmailSubmit(Some(testEmail))
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.confirmEmail.url)
      }

      "handle existing digital account holders and redirect them to nsi" in {
        val userInfo = randomEligibleWithUserInfo(validUserInfo)
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(Right(Some(HTSSession(Some(Right(userInfo)), Some("email"), None))))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(Some("email")))
        }
        val result = selectEmailSubmit(Some(testEmail))
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(nsiAccountHomeURL)
      }

      "handle DE users - throw server error if no existing session found" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(Right(None))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
        }

        val result = selectEmailSubmit(None)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility.url)
      }

      "handle DE users - throw server error if there is an existing session but no email" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(Right(Some(HTSSession(None, None, None))))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
        }

        val result = selectEmailSubmit(None)
        status(result) shouldBe 500
      }

      "handle DE users who submitted form with no new-email but with checked existing email" in {
        val session = HTSSession(None, None, Some(testEmail))
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsWithEmail(None))
          mockSessionStoreGet(Right(Some(session)))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
          mockEncrypt("email@gmail.com")(encryptedEmail)
          mockSessionStorePut(session.copy(hasSelectedEmail = true))(Right(()))
        }

        val result = selectEmailSubmit(None)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some("/help-to-save/confirm-email/encrypted")
      }

      "handle DE user who submitted form with new-email" in {
        val userInfo = randomEligibleWithUserInfo(validUserInfo)
        val session = HTSSession(Some(Right(userInfo)), None, Some(testEmail))
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(Right(Some(session)))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
          mockSessionStorePut(session.copy(hasSelectedEmail = true))(Right(()))
        }

        val result = selectEmailSubmit(Some(testEmail))
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.confirmEmail.url)
      }

      "handle DE user who submitted form with errors" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(Right(Some(HTSSession(None, None, Some(testEmail)))))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
        }

        val result = selectEmailSubmit(Some("invalidEmail"))
        status(result) shouldBe 200
        contentAsString(result) should include("What email address do you want to use for your Help to Save account?")
      }

      "handle an existing account holder who submitted form with no new-email but with checked existing email" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsWithEmail(None))
          mockSessionStoreGet(Right(Some(HTSSession(None, None, Some(testEmail)))))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(Some(testEmail)))
        }

        val result = selectEmailSubmit(None)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(nsiAccountHomeURL)
      }
    }

    "handling selectEmailSubmitReminder requests" must {

      def selectEmailSubmitReminder(newEmail: Option[String]): Future[Result] =
        newEmail.fold(
          csrfAddToken(controller.selectEmailSubmitReminder())(fakeRequest.withFormUrlEncodedBody("email" → "Yes"))
        ) { e ⇒
          csrfAddToken(controller.selectEmailSubmitReminder())(
            fakeRequest.withFormUrlEncodedBody("email" → "No", "new-email" → e)
          )
        }

      "handle Digital(new applicant) users with no valid session in mongo" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(Right(None))
          mockEnrolmentCheck()(Right(NotEnrolled))
        }

        val result = selectEmailSubmitReminder(None)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility.url)
      }

      "handle errors during session cache lookup in mongo" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(Left("unexpected error"))
        }

        val result = selectEmailSubmitReminder(None)
        status(result) shouldBe 500
      }

      "handle Digital(new applicant) who submitted form with no new-email but with checked existing email" in {
        val session = HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, None)
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(Right(Some(session)))
          mockEnrolmentCheck()(Right(NotEnrolled))
          mockEncrypt(emailStr)(encryptedEmail)
          mockSessionStorePut(session.copy(hasSelectedEmail = true))(Right(()))
        }

        val result = selectEmailSubmitReminder(None)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some("/help-to-save/confirm-email/encrypted")
      }

      "handle Digital(new applicant) who submitted form with new-email" in {

        val userInfo = randomEligibleWithUserInfo(validUserInfo)
        val session = HTSSession(
          Some(Right(userInfo)),
          None,
          None,
          None,
          None,
          Some(BankDetails(SortCode(1, 2, 3, 4, 5, 6), "1", None, "name"))
        )

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(Right(Some(session)))
          mockEnrolmentCheck()(Right(NotEnrolled))
          mockSessionStorePut(session.copy(pendingEmail = Some(testEmail), hasSelectedEmail = true))(Right(None))
        }

        val result = selectEmailSubmitReminder(Some(testEmail))
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.confirmEmail.url)
      }

      "handle existing digital account holders and redirect them to nsi" in {
        val userInfo = randomEligibleWithUserInfo(validUserInfo)
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(Right(Some(HTSSession(Some(Right(userInfo)), Some("email"), None))))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(Some("email")))
        }
        val result = selectEmailSubmitReminder(Some(testEmail))
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(nsiAccountHomeURL)
      }

      "handle DE users - throw server error if no existing session found" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(Right(None))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
        }

        val result = selectEmailSubmitReminder(None)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility.url)
      }

      "handle DE users - throw server error if there is an existing session but no email" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(Right(Some(HTSSession(None, None, None))))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
        }

        val result = selectEmailSubmitReminder(None)
        status(result) shouldBe 500
      }

      "handle DE users who submitted form with no new-email but with checked existing email" in {
        val session = HTSSession(None, None, Some(testEmail))
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsWithEmail(None))
          mockSessionStoreGet(Right(Some(session)))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
          mockEncrypt("email@gmail.com")(encryptedEmail)
          mockSessionStorePut(session.copy(hasSelectedEmail = true))(Right(()))
        }

        val result = selectEmailSubmitReminder(None)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some("/help-to-save/confirm-email/encrypted")
      }

      "handle DE user who submitted form with new-email" in {
        val userInfo = randomEligibleWithUserInfo(validUserInfo)
        val session = HTSSession(Some(Right(userInfo)), None, Some(testEmail))
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(Right(Some(session)))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
          mockSessionStorePut(session.copy(hasSelectedEmail = true))(Right(()))
        }

        val result = selectEmailSubmitReminder(Some(testEmail))
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.confirmEmail.url)
      }

      "handle DE user who submitted form with errors" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(Right(Some(HTSSession(None, None, Some(testEmail)))))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
        }

        val result = selectEmailSubmitReminder(Some("invalidEmail"))
        status(result) shouldBe 200
        contentAsString(result) should include("What email address do you want to use for your Help to Save account?")
      }

      "handle an existing account holder who submitted form with no new-email but with checked existing email" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsWithEmail(None))
          mockSessionStoreGet(Right(Some(HTSSession(None, None, Some(testEmail)))))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(Some(testEmail)))
        }

        val result = selectEmailSubmitReminder(None)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(nsiAccountHomeURL)
      }
    }

    "handling getGiveEmailPage requests" must {
      def getGiveEmailPage(): Future[Result] = csrfAddToken(controller.getGiveEmailPage)(fakeRequest)

      "handle Digital(new applicant) users with an existing valid email from GG but not gone through eligibility checks" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(Right(None))
          mockEnrolmentCheck()(Right(NotEnrolled))
        }

        val result = getGiveEmailPage()
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility.url)
      }

      "handle Digital(new applicant) users with an existing valid email from GG and already gone through eligibility checks" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(
            Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, None)))
          )
          mockEnrolmentCheck()(Right(NotEnrolled))
        }

        val result = getGiveEmailPage()
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.getSelectEmailPage.url)
      }

      "handle Digital(new applicant) users with an existing INVALID email from GG and should display giveEmailPage" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(
            Right(
              Some(
                HTSSession(
                  Some(Right(randomEligibleWithUserInfo(validUserInfo).withEmail(Some("invalidEmail")))),
                  None,
                  None
                )
              )
            )
          )
          mockEnrolmentCheck()(Right(NotEnrolled))
        }

        val result = getGiveEmailPage()
        status(result) shouldBe 200
        contentAsString(result) should include("Which email address do you want to use for Help to Save?")
      }

      "use correct back link for digital applicants when they come from check details page" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(
            Right(
              Some(
                HTSSession(
                  Some(Right(randomEligibleWithUserInfo(validUserInfo).withEmail(Some("invalidEmail")))),
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
          mockEnrolmentCheck()(Right(NotEnrolled))
        }

        val result = getGiveEmailPage()
        status(result) shouldBe 200
        contentAsString(result) should include("Which email address do you want to use for Help to Save?")
        contentAsString(result) should include("/help-to-save/create-account")
      }

      "handle existing digital account holders and redirect them to nsi" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(Right(None))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(Some("email")))
        }

        val result = getGiveEmailPage()
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(nsiAccountHomeURL)
      }

      "handle DE users with an existing valid email from GG" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(Right(None))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
          mockSessionStorePut(HTSSession(None, None, Some("tyrion_lannister@gmail.com"), None, None))(Right(None))
        }

        val result = getGiveEmailPage()
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.getSelectEmailPage.url)
      }

      "DE users should not contain any Back link" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsWithEmail(None))
          mockSessionStoreGet(Right(Some(HTSSession(None, None, None))))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
          mockSessionStorePut(HTSSession(None, None, None))(Right(None))
        }

        val result = getGiveEmailPage()
        status(result) shouldBe 200
        contentAsString(result) should include("Which email address do you want to use for Help to Save?")
      }

      "handle DE users with an existing INVALID email from GG" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsWithEmail(Some("invalidEmail")))
          mockSessionStoreGet(Right(None))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
          mockSessionStorePut(HTSSession(None, None, None, None, None))(Right(None))
        }

        val result = getGiveEmailPage()
        status(result) shouldBe 200
        contentAsString(result) should include("Which email address do you want to use for Help to Save?")
      }

      "handle DE users with NO email from GG" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsWithEmail(None))
          mockSessionStoreGet(Right(None))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
          mockSessionStorePut(HTSSession(None, None, None, None, None))(Right(None))
        }

        val result = getGiveEmailPage()
        status(result) shouldBe 200
        contentAsString(result) should include("Which email address do you want to use for Help to Save?")
      }

      "handle DE users with an Missing UserInfo from Auth" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsMissingUserInfo)
          mockSessionStoreGet(Right(None))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
          mockSessionStorePut(HTSSession(None, None, None, None, None))(Right(None))
        }

        val result = getGiveEmailPage()
        status(result) shouldBe 200
        contentAsString(result) should include("Which email address do you want to use for Help to Save?")
      }

      "handle unexpected errors during enrolment check" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(Right(None))
          mockEnrolmentCheck()(Left("unexpected error"))
        }

        val result = getGiveEmailPage()
        status(result) shouldBe 500
      }

      "redirect to NS&I if request comes from already enrolled Digital users with valid email in mongo" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(Right(None))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(Some(testEmail)))
        }

        val result = getGiveEmailPage()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(nsiAccountHomeURL)
      }

    }

    "handling giveEmailSubmit requests" must {

      val email = "email@test.com"

      def giveEmailSubmit(email: String): Future[Result] =
        csrfAddToken(controller.giveEmailSubmit())(fakeRequest.withFormUrlEncodedBody("email" → email))

      "handle Digital(new applicant) users with no valid session in mongo" in {

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionStoreGet(Right(None))
          mockEnrolmentCheck()(Right(NotEnrolled))
        }

        val result = giveEmailSubmit(email)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility.url)
      }

      "handle errors during session cache lookup in mongo" in {

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionStoreGet(Left("unexpected error"))
        }

        val result = giveEmailSubmit(email)
        status(result) shouldBe 500
      }

      "handle Digital(new applicant) who submitted form with new email" in {
        val userInfo = randomEligibleWithUserInfo(validUserInfo)
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionStoreGet(
            Right(
              Some(
                HTSSession(
                  Some(Right(userInfo)),
                  None,
                  None,
                  None,
                  None,
                  Some(BankDetails(SortCode(1, 2, 3, 4, 5, 6), "1", None, "name"))
                )
              )
            )
          )
          mockEnrolmentCheck()(Right(NotEnrolled))
          mockSessionStorePut(
            HTSSession(
              Some(Right(userInfo)),
              None,
              Some(email),
              None,
              None,
              Some(BankDetails(SortCode(1, 2, 3, 4, 5, 6), "1", None, "name"))
            )
          )(Right(None))
        }

        val result = giveEmailSubmit(email)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.confirmEmail.url)
      }

      "handle existing digital account holders and redirect them to nsi" in {
        val userInfo = randomEligibleWithUserInfo(validUserInfo)
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionStoreGet(Right(Some(HTSSession(Some(Right(userInfo)), None, Some(testEmail)))))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(Some("email")))
        }
        val result = giveEmailSubmit(testEmail)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(nsiAccountHomeURL)
      }

      "handle DE users - redirect to check eligibility if no existing session found" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionStoreGet(Right(None))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
        }

        val result = giveEmailSubmit(email)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility.url)
      }

      "handle DE user who submitted form with new-email" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionStoreGet(Right(Some(HTSSession(None, None, None))))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
          mockSessionStorePut(HTSSession(None, None, Some(email)))(Right(()))
        }

        val result = giveEmailSubmit(email)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.confirmEmail.url)
      }

      "handle DE user who submitted form with errors" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionStoreGet(Right(Some(HTSSession(None, None, Some(testEmail)))))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
        }

        val result = giveEmailSubmit("badEmail")
        status(result) shouldBe 200
        contentAsString(result) should include("Which email address do you want to use for Help to Save?")
      }
    }

    "handling emailConfirmed requests in reminder journey " must {

      val userInfo = randomEligibleWithUserInfo(validUserInfo)
      randomEligibleWithUserInfo(validUserInfo).withEmail(Some("invalidEmail"))

      def emailConfirmed(encryptedEmail: String): Future[Result] =
        csrfAddToken(controller.emailConfirmed(encryptedEmail))(fakeRequest)

      "handle Digital(new applicant) users with an existing valid email from GG, already gone through eligibility checks and no bank details in session" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockDecrypt("encrypted")("decrypted")
          mockSessionStoreGet(Right(Some(HTSSession(Some(Right(userInfo)), None, None))))
          mockEnrolmentCheck()(Right(NotEnrolled))
          mockSessionStorePut(HTSSession(Some(Right(userInfo)), Some("decrypted"), None))(Right(None))
          mockStoreConfirmedEmail("decrypted")(Right(None))
        }

        val result = emailConfirmed(encryptedEmail)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.ReminderController.getApplySavingsReminderPage.url)
      }
    }

    "handling emailConfirmed requests" must {

      val email = "test@user.com"
      val userInfo = randomEligibleWithUserInfo(validUserInfo)
      val userInfoWithInvalidEmail = randomEligibleWithUserInfo(validUserInfo).withEmail(Some("invalidEmail"))

      def emailConfirmed(encryptedEmail: String): Future[Result] =
        csrfAddToken(controller.emailConfirmed(encryptedEmail))(fakeRequest)

      "handle Digital(new applicant) users with an existing valid email from GG but not gone through eligibility checks" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockDecrypt("encrypted")("decrypted")
          mockSessionStoreGet(Right(Some(HTSSession(None, None, None))))
          mockEnrolmentCheck()(Right(NotEnrolled))
        }

        val result = emailConfirmed(encryptedEmail)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility.url)
      }

      "handle Digital(new applicant) users with an existing valid email from GG, already gone through eligibility checks and no bank details in session" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockDecrypt("encrypted")("decrypted")
          mockSessionStoreGet(Right(Some(HTSSession(Some(Right(userInfo)), None, None))))
          mockEnrolmentCheck()(Right(NotEnrolled))
          mockSessionStorePut(HTSSession(Some(Right(userInfo)), Some("decrypted"), None))(Right(None))
          mockStoreConfirmedEmail("decrypted")(Right(None))
        }

        val result = emailConfirmed(encryptedEmail)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.ReminderController.getApplySavingsReminderPage.url)
      }

      "handle Digital(new applicant) users with an existing valid email from GG, already gone through eligibility checks but bank details are already in session" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockDecrypt("encrypted")("decrypted")
          mockSessionStoreGet(
            Right(
              Some(
                HTSSession(
                  Some(Right(userInfo)),
                  None,
                  None,
                  None,
                  None,
                  Some(BankDetails(SortCode(1, 2, 3, 4, 5, 6), "1", Some("1"), "a")),
                  None,
                  None,
                  true
                )
              )
            )
          )
          mockEnrolmentCheck()(Right(NotEnrolled))
          mockSessionStorePut(
            HTSSession(
              Some(Right(userInfo)),
              Some("decrypted"),
              None,
              None,
              None,
              Some(BankDetails(SortCode(1, 2, 3, 4, 5, 6), "1", Some("1"), "a")),
              None,
              None,
              true
            )
          )(Right(None))
          mockStoreConfirmedEmail("decrypted")(Right(None))
        }

        val result = emailConfirmed(encryptedEmail)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.RegisterController.getCreateAccountPage.url)
      }

      "handle Digital(new applicant) users with an existing INVALID email from GG and already gone through eligibility checks" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockDecrypt("encrypted")("decrypted")
          mockSessionStoreGet(Right(Some(HTSSession(Some(Right(userInfoWithInvalidEmail)), None, None))))
          mockEnrolmentCheck()(Right(NotEnrolled))
        }

        val result = emailConfirmed(encryptedEmail)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.getGiveEmailPage.url)
      }

      "handle existing digital account holders and redirect them to nsi" in {
        val newEmail = "email@hmrc.com"
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockDecrypt("encrypted")("decrypted")
          mockSessionStoreGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), Some(newEmail), None))))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(Some("email@email.com")))
        }

        val result = emailConfirmed(encryptedEmail)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(nsiAccountHomeURL)
      }

      "handle DE users with an existing valid email from GG" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockDecrypt("encrypted")("decrypted")
          mockSessionStoreGet(Right(Some(HTSSession(Some(Right(userInfo)), None, Some(email)))))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
          mockSessionStorePut(HTSSession(None, Some("decrypted"), None))(Right(None))
          mockStoreConfirmedEmail("decrypted")(Right(None))
          mockUpdateEmail(
            NSIPayload(userInfo.userInfo.copy(email = Some("decrypted")), "decrypted", version, systemId)
          )(Right(None))
          mockAudit(
            EmailChanged(nino, "", "decrypted", false, routes.EmailController.emailConfirmed(encryptedEmail).url)
          )
        }

        val result = emailConfirmed(encryptedEmail)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(nsiAccountHomeURL)
      }
    }

    "handling emailConfirmedCallback requests" must {

      def emailConfirmedCallback(emailVerificationParams: String): Future[Result] =
        csrfAddToken(controller.emailConfirmedCallback(emailVerificationParams))(fakeRequest)

      val email = "test@user.com"

      val encryptedParams = new String(Base64.getEncoder.encode("encrypted".getBytes))

      val eligibleWithUserInfo = randomEligibleWithUserInfo(validUserInfo.copy(email = Some(email), nino = nino))

      "handle Digital users and return success result" in {
        val newEmail = "new@email.com"

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockDecrypt("encrypted")(s"$nino#$newEmail")
          mockSessionStoreGet(
            Right(Some(HTSSession(Some(Right(eligibleWithUserInfo)), None, None, changingDetails = true)))
          )
          mockSessionStorePut(
            HTSSession(
              Some(Right(eligibleWithUserInfo.withEmail(Some(newEmail)))),
              Some(newEmail),
              None,
              changingDetails = true
            )
          )(Right(None))
          mockStoreConfirmedEmail(newEmail)(Right(None))
          mockAudit(
            EmailChanged(
              nino,
              email,
              newEmail,
              true,
              routes.EmailController.emailConfirmedCallback(encryptedParams).url
            )
          )
        }

        val result = emailConfirmedCallback(encryptedParams)

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.getEmailConfirmed.url)

      }

      "handle Digital users and return success result when there is no GG email" in {
        val newEmail = "new@email.com"

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsWithEmail(None))
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockDecrypt("encrypted")(s"$nino#$newEmail")
          mockSessionStoreGet(Right(Some(HTSSession(Some(Right(eligibleWithUserInfo.withEmail(None))), None, None))))
          mockSessionStorePut(
            HTSSession(Some(Right(eligibleWithUserInfo.withEmail(Some(newEmail)))), Some(newEmail), None)
          )(Right(None))
          mockStoreConfirmedEmail(newEmail)(Right(None))
          mockAudit(
            EmailChanged(nino, "", newEmail, true, routes.EmailController.emailConfirmedCallback(encryptedParams).url)
          )
        }

        val result = emailConfirmedCallback(encryptedParams)

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.getEmailConfirmed.url)

      }

      "handle Digital users and return server error when NINOs do not match" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockDecrypt("encrypted")("AE123456C#test@user.com")
          mockSessionStoreGet(Right(Some(HTSSession(Some(Right(eligibleWithUserInfo)), None, None))))
        }

        val result = emailConfirmedCallback(encryptedParams)

        status(result) shouldBe 500
      }

      "handle Digital errors when email verification params CANNOT be decoded" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockAudit(
            SuspiciousActivity(
              Some(nino),
              "malformed_redirect",
              routes.EmailController.emailConfirmedCallback("blah blah").url
            )
          )
        }

        val result = emailConfirmedCallback("blah blah")

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.confirmEmailErrorTryLater.url)
      }

      "handle Digital users who have not gone through eligibility checks and are eligible" in {
        val newEmail = "new@email.com"

        val eligibilityResult = randomEligibleWithUserInfo(validUserInfo.copy(email = Some(email)))

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockDecrypt("encrypted")(s"$nino#$newEmail")
          mockSessionStoreGet(Right(Some(HTSSession(Some(Right(eligibilityResult)), Some(email), None))))
          mockSessionStorePut(
            HTSSession(Some(Right(eligibilityResult.withEmail(Some(newEmail)))), Some(newEmail), None)
          )(Right(None))
          mockStoreConfirmedEmail(newEmail)(Right(None))
          mockAudit(
            EmailChanged(
              nino,
              email,
              newEmail,
              true,
              routes.EmailController.emailConfirmedCallback(encryptedParams).url
            )
          )
        }

        val result = emailConfirmedCallback(encryptedParams)

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.getEmailConfirmed.url)
      }

      "handle Digital users who have not gone through eligibility checks and not eligible" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockDecrypt("encrypted")("WM123456C#test@user.com")
          mockSessionStoreGet(Right(Some(HTSSession(None, Some(email), None))))
          mockEligibilityResult()(Right(EligibilityCheckResultType.Ineligible(randomEligibilityResponse())))
        }

        val result = emailConfirmedCallback(encryptedParams)

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsNotEligible.url)
      }

      "handle unexpected errors during enrolment check" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck()(Left("error"))
        }

        val result = emailConfirmedCallback(encryptedParams)

        status(result) shouldBe 500
      }

      "handle DE users with missing user info" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsMissingUserInfo)
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
        }

        val result = emailConfirmedCallback(encryptedParams)

        status(result) shouldBe 500
      }

      "handle DE users and update email successfully with NS&I" in {

        val updatedNSIPayload = NSIPayload(validUserInfo.copy(email = Some(email)), email, version, systemId)
        val updateReminderEmail = UpdateReminderEmail(nino, email, firstName, lastName)

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
          mockDecrypt("encrypted")(s"WM123456C#$email")
          mockUpdateEmail(updatedNSIPayload)(Right(None))
          mockStoreConfirmedEmail(email)(Right(None))
          mockStoreConfirmedEmailInReminders(updateReminderEmail)(Right((())))
          mockSessionStorePut(HTSSession(None, Some(email), None))(Right(None))
          mockAudit(
            EmailChanged(nino, "", email, false, routes.EmailController.emailConfirmedCallback(encryptedParams).url)
          )
        }

        val result = emailConfirmedCallback(encryptedParams)

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.getEmailConfirmed.url)
      }

      "handle DE users and handle errors during updating email with NS&I" in {

        val updatedNSIUserInfo = NSIPayload(validUserInfo.copy(email = Some(email)), email, version, systemId)
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
          mockDecrypt("encrypted")("WM123456C#test@user.com")
          mockUpdateEmail(updatedNSIUserInfo)(Left("error"))
        }

        val result = emailConfirmedCallback(encryptedParams)
        status(result) shouldBe 500
      }

      "handle DE errors when email verification params CANNOT be decoded" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
          mockAudit(
            SuspiciousActivity(
              Some(nino),
              "malformed_redirect",
              routes.EmailController.emailConfirmedCallback("blah blah").url
            )
          )
        }

        val result = emailConfirmedCallback("blah blah")

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.confirmEmailErrorTryLater.url)
      }

    }

    "handling confirmEmail requests" must {
      def confirmEmail: Future[Result] =
        csrfAddToken(controller.confirmEmail)(fakeRequest)

      "handle Digital users and return the check your email page with a status of Ok" in {

        val newEmail = "email@hmrc.com"
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), None, Some(newEmail)))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockEmailVerification(nino, newEmail, firstName)(Right(()))
        }

        val result = confirmEmail
        status(result) shouldBe 200
        contentAsString(result).contains(messages("hts.email-verification.check-your-email.title.h1")) shouldBe true
        contentAsString(result).contains(messages("hts.email-verification.check-your-email.content1")) shouldBe true
      }

      "handle Digital users who have not gone through eligibility checks" in {
        val newEmail = "email@hmrc.com"
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(Right(Some(HTSSession(None, None, Some(newEmail)))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        }

        val result = confirmEmail
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility.url)
      }

      "handle Digital users who have already verified their email" in {
        val newEmail = "email@hmrc.com"
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), None, Some(newEmail)))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockEmailVerification(nino, newEmail, firstName)(Left(AlreadyVerified))
          mockEncrypt("WM123456C#email@hmrc.com")("decrypted")
        }

        val result = confirmEmail
        status(result) shouldBe 303
        redirectLocation(result).getOrElse("") should include("/help-to-save/email-confirmed-callback")
      }

      "handle existing digital account holders and redirect them to nsi" in {
        val newEmail = "email@hmrc.com"
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), Some(newEmail), None))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
          mockGetConfirmedEmail()(Right(Some("email@email.com")))
        }

        val result = confirmEmail
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(nsiAccountHomeURL)
      }

      "handle DE users with pending email in the session" in {
        val newEmail = "email@hmrc.com"
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), None, Some(newEmail)))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
          mockEmailVerification(nino, newEmail, firstName)(Right(()))
        }

        val result = confirmEmail
        status(result) shouldBe 200
        contentAsString(result).contains(messages("hts.email-verification.check-your-email.title.h1")) shouldBe true
        contentAsString(result).contains(messages("hts.email-verification.check-your-email.content1")) shouldBe true
      }

      "handle DE users with missing user info from GG" in {
        val newEmail = "email@hmrc.com"
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsMissingUserInfo)
          mockSessionStoreGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), Some(newEmail), None))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
        }

        val result = confirmEmail
        status(result) shouldBe 500
      }

      "handle DE users with NO stored pending email in the session" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), None, None))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
        }

        val result = confirmEmail
        status(result) shouldBe 500
      }
    }

    "handling confirmEmailError requests" must {

      def confirmEmailError: Future[Result] =
        csrfAddToken(controller.confirmEmailError)(fakeRequest)

      "handle Digital users who are already gone through eligibility checks" in {
        val newEmail = "email@hmrc.com"
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), Some(newEmail), None))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        }

        val result = confirmEmailError
        status(result) shouldBe 200
        contentAsString(result) should include("We cannot change your email address at the moment")
      }

      "handle existing digital account holders and redirect them to nsi" in {
        val newEmail = "email@hmrc.com"
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), Some(newEmail), None))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
          mockGetConfirmedEmail()(Right(Some("email@email.com")))
        }

        val result = confirmEmailError
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(nsiAccountHomeURL)
      }

      "handle DE users" in {
        val newEmail = "email@hmrc.com"
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionStoreGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), Some(newEmail), None))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
        }

        val result = confirmEmailError
        status(result) shouldBe 200
        contentAsString(result) should include("We cannot change your email address at the moment")
      }

    }

    "handling confirmEmailErrorTryLater requests" must {
      def confirmEmailErrorTryLater: Future[Result] =
        csrfAddToken(controller.confirmEmailErrorTryLater)(fakeRequest)

      "handle Digital users who are already gone through eligibility checks" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionStoreGet(Right(Some(HTSSession(None, None, None))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        }

        val result = confirmEmailErrorTryLater
        status(result) shouldBe 200
        contentAsString(result) should include("We cannot change your email")
      }

      "handle DE users" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionStoreGet(Right(Some(HTSSession(None, None, None))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
        }

        val result = confirmEmailErrorTryLater
        status(result) shouldBe 200
        contentAsString(result) should include("We cannot change your email address at the moment")
      }
    }

    "handling confirmEmailErrorSubmit requests" must {

      def confirmEmailErrorSubmit(continue: Boolean): Future[Result] =
        csrfAddToken(controller.confirmEmailErrorSubmit())(
          fakeRequest.withFormUrlEncodedBody("radio-inline-group" → continue.toString)
        )

      "handle Digital users and redirect to the email verify error page try later if there is no email for the user" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionStoreGet(
            Right(
              Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo.copy(email = None)))), None, None))
            )
          )
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        }

        val result = confirmEmailErrorSubmit(true)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.confirmEmailErrorTryLater.url)
      }

      "handle Digital users and redirect to the emailConfirmed endpoint if there is an email for the user and the user selects to continue" in {

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionStoreGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), None, None))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockEncrypt(emailStr)(encryptedEmail)
        }

        val result = confirmEmailErrorSubmit(true)
        status(result) shouldBe 303

        redirectLocation(result) shouldBe Some(routes.EmailController.emailConfirmed(encryptedEmail).url)
      }

      "handle Digital users and redirect to the info endpoint if there is an email for the user and the user selects not to continue" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionStoreGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), None, None))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        }

        val result = confirmEmailErrorSubmit(false)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.IntroductionController.getAboutHelpToSave.url)
      }

      "handle Digital users and show the verify email error page again if there is an error in the form" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionStoreGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), None, None))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        }

        val result = csrfAddToken(controller.confirmEmailErrorSubmit())(fakeRequest)
        status(result) shouldBe 200
        contentAsString(result) should include("We cannot change your email address at the moment")
      }

      "handle existing digital account holders and redirect them to nsi" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionStoreGet(Right(Some(HTSSession(None, None, None))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
          mockGetConfirmedEmail()(Right(Some("email@email.com")))
        }

        val result = csrfAddToken(controller.confirmEmailErrorSubmit())(fakeRequest)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(nsiAccountHomeURL)
      }

      "handle DE users and redirect to the emailConfirmed endpoint if there is an email for the user and the user selects to continue" in {

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionStoreGet(Right(Some(HTSSession(None, Some(emailStr), None))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
          mockEncrypt(emailStr)(encryptedEmail)
        }

        val result = confirmEmailErrorSubmit(true)
        status(result) shouldBe 303

        redirectLocation(result) shouldBe Some(routes.EmailController.emailConfirmed(encryptedEmail).url)

      }
    }

    "handling getEmailConfirmed" must {

      def getEmailConfirmed = csrfAddToken(controller.getEmailConfirmed)(fakeRequest)

      "handle Digital users and return the email verified page" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionStoreGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), Some("email"), None))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        }

        val result = getEmailConfirmed
        status(result) shouldBe OK
        contentAsString(result) should include("You have confirmed the email address")
      }

      "handle Digital users and redirect to check eligibility" when {

        "there is no session" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockSessionStoreGet(Right(None))
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          }

          val result = getEmailConfirmed
          status(result) shouldBe 303
          redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility.url)
        }

      }

      "handle Digital users and return an error" when {

        "there is no confirmed email in the session" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockSessionStoreGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), None, None))))
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          }

          val result = getEmailConfirmed
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.EmailController.confirmEmailError.url)
        }

        "there is no confirmed email in the session when there is no email for the user" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockSessionStoreGet(
              Right(
                Some(
                  HTSSession(
                    Some(Right(eligibleWithValidUserInfo.copy(userInfo = validUserInfo.copy(email = None)))),
                    None,
                    None
                  )
                )
              )
            )
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          }

          val result = getEmailConfirmed
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.EmailController.confirmEmailErrorTryLater.url)
        }

        "the call to session cache fails" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockSessionStoreGet(Left(""))
          }

          val result = getEmailConfirmed
          checkIsTechnicalErrorPage(result)
        }
      }

      "handle DE users and return the give email page" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionStoreGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), Some("email"), None))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
        }

        val result = getEmailConfirmed
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/help-to-save/enter-email")
      }

    }

    "handling getEmailUpdated" must {

      def getEmailUpdated(): Future[Result] =
        csrfAddToken(controller.getEmailUpdated())(fakeRequest)

      "show the email updated page otherwise" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionStoreGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), None, None))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        }

        val result = getEmailUpdated()
        status(result) shouldBe OK
        contentAsString(result) should include("You have confirmed the email address")

      }

    }

    "handling emailUpdatedSubmit" must {

      "handle Digital users and redirect to the getBankDetailsPage if bank details are not already in session" in {

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionStoreGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), None, None))))
          mockEnrolmentCheck()(Right(NotEnrolled))
        }

        val result = controller.emailUpdatedSubmit()(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.BankAccountController.getBankDetailsPage.url)
      }

      "handle Digital users and redirect to the checkDetailsPage if the user is in the process of changing details" in {

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionStoreGet(
            Right(
              Some(
                HTSSession(
                  Some(Right(eligibleWithValidUserInfo)),
                  None,
                  None,
                  None,
                  None,
                  Some(BankDetails(SortCode(1, 2, 3, 4, 5, 6), "1", None, "name")),
                  changingDetails = true
                )
              )
            )
          )
          mockEnrolmentCheck()(Right(NotEnrolled))
        }

        val result = controller.emailUpdatedSubmit()(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.RegisterController.getCreateAccountPage.url)
      }

      "handle Digital users and redirect to the eligibilityCheck if session doesnt contain eligibility result" in {

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionStoreGet(Right(None))
          mockEnrolmentCheck()(Right(NotEnrolled))
        }

        val result = controller.emailUpdatedSubmit()(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility.url)
      }

      "handle existing digital account holders and redirect them to NSI" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionStoreGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), Some("email"), None))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
          mockGetConfirmedEmail()(Right(Some("email")))
        }

        val result = controller.emailUpdatedSubmit()(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(nsiAccountHomeURL)
      }

      "handle DE users and redirect to the give email page" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionStoreGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), None, None))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
        }

        val result = controller.emailUpdatedSubmit()(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/help-to-save/enter-email")
      }

    }
  }
}

object EmailControllerSpec {

  implicit class EligibleWithUserInfoOps(val e: EligibleWithUserInfo) extends AnyVal {

    def withEmail(email: Option[String]): EligibleWithUserInfo =
      e.copy(userInfo = e.userInfo.copy(email = email))
  }

}
