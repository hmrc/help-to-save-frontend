/*
 * Copyright 2018 HM Revenue & Customs
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
import play.api.i18n.Messages
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.connectors.EmailVerificationConnector
import uk.gov.hmrc.helptosavefrontend.controllers.EmailControllerSpec.EligibleWithUserInfoOps
import uk.gov.hmrc.helptosavefrontend.forms.BankDetails
import uk.gov.hmrc.helptosavefrontend.models.EnrolmentStatus.{Enrolled, NotEnrolled}
import uk.gov.hmrc.helptosavefrontend.models.HTSSession.EligibleWithUserInfo
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithCL200
import uk.gov.hmrc.helptosavefrontend.models.TestData.Eligibility._
import uk.gov.hmrc.helptosavefrontend.models.TestData.UserData.validUserInfo
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityCheckResult
import uk.gov.hmrc.helptosavefrontend.models.email.VerifyEmailError
import uk.gov.hmrc.helptosavefrontend.models.email.VerifyEmailError.AlreadyVerified
import uk.gov.hmrc.helptosavefrontend.models.userinfo.NSIPayload
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, NINO}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditResult

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class EmailControllerSpec
  extends AuthSupport
  with CSRFSupport
  with EnrolmentAndEligibilityCheckBehaviour
  with SessionCacheBehaviourSupport {

  lazy val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  lazy val messages: Messages = messagesApi.preferred(request)

  val mockEmailVerificationConnector: EmailVerificationConnector = mock[EmailVerificationConnector]

  val mockAuditor = mock[HTSAuditor]

  override implicit val crypto: Crypto = mock[Crypto]
  val encryptedEmail = "encrypted"

  def newController()(implicit crypto: Crypto) =
    new EmailController(
      mockHelpToSaveService,
      mockSessionCacheConnector,
      mockEmailVerificationConnector,
      mockAuthConnector,
      mockMetrics,
      fakeApplication,
      mockAuditor
    ) {
      override val authConnector = mockAuthConnector
    }

  lazy val controller = newController()

  val eligibleWithValidUserInfo = randomEligibleWithUserInfo(validUserInfo)

  def mockEmailVerification(nino: String, email: String, firstName: String)(result: Either[VerifyEmailError, Unit]) = {
    (mockEmailVerificationConnector.verifyEmail(_: String, _: String, _: String, _: Boolean)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nino, email, firstName, true, *, *)
      .returning(Future.successful(result))
  }

  def mockAudit(expectedEvent: HTSEvent) =
    (mockAuditor.sendEvent(_: HTSEvent, _: NINO)(_: ExecutionContext))
      .expects(expectedEvent, *, *)
      .returning(Future.successful(AuditResult.Success))

  def mockStoreConfirmedEmail(email: String)(result: Either[String, Unit]): Unit =
    (mockHelpToSaveService.storeConfirmedEmail(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(email, *, *)
      .returning(EitherT.fromEither[Future](result))

  def mockGetConfirmedEmail()(result: Either[String, Option[String]]): Unit =
    (mockHelpToSaveService.getConfirmedEmail()(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *)
      .returning(EitherT.fromEither[Future](result))

  def mockEligibilityResult()(result: Either[String, EligibilityCheckResult]): Unit =
    (mockHelpToSaveService.checkEligibility()(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *)
      .returning(EitherT.fromEither[Future](result))

  def mockUpdateEmail(nsiPayload: NSIPayload)(result: Either[String, Unit]): Unit =
    (mockHelpToSaveService.updateEmail(_: NSIPayload)(_: HeaderCarrier, _: ExecutionContext))
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

    "handling getSelectEmailPage requests" must {

        def getSelectEmailPage(): Future[Result] = controller.getSelectEmailPage(fakeRequestWithCSRFToken)

      "handle Digital(new applicant) users with an existing valid email from GG but not gone through eligibility checks" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionCacheConnectorGet(Right(None))
          mockEnrolmentCheck()(Right(NotEnrolled))
        }

        val result = getSelectEmailPage()
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility().url)
      }

      "handle Digital(new applicant) users with an existing valid email from GG and already gone through eligibility checks" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, None))))
          mockEnrolmentCheck()(Right(NotEnrolled))
        }

        val result = getSelectEmailPage()
        status(result) shouldBe 200
        contentAsString(result) should include("Which email address do you want to use for your Help to Save account?")
      }

      "handle Digital(new applicant) users with an existing INVALID email from GG and should display giveEmailPage" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo).withEmail(Some("invalidEmail")))), None, None))))
          mockEnrolmentCheck()(Right(NotEnrolled))
        }

        val result = getSelectEmailPage()
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.getGiveEmailPage().url)
      }

      "use correct back link for digital applicants when they come from check details page" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, None, None, None, None, true))))
          mockEnrolmentCheck()(Right(NotEnrolled))
        }

        val result = getSelectEmailPage()
        status(result) shouldBe 200
        contentAsString(result) should include("Which email address do you want to use for your Help to Save account?")
        contentAsString(result) should include("/help-to-save/check-details")
      }

      "handle DE users with an existing valid email from GG" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None, None))))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
          mockSessionCacheConnectorPut(HTSSession(None, None, Some("tyrion_lannister@gmail.com"), None, None))(Right(None))
        }

        val result = getSelectEmailPage()
        status(result) shouldBe 200
        contentAsString(result) should include("Which email address do you want to use for your Help to Save account?")
      }

      "DE users should not contain any Back link" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None, None))))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
          mockSessionCacheConnectorPut(HTSSession(None, None, Some("tyrion_lannister@gmail.com")))(Right(None))
        }

        val result = getSelectEmailPage()
        status(result) shouldBe 200
        contentAsString(result) should include("Which email address do you want to use for your Help to Save account?")
      }

      "handle DE users with an existing INVALID email from GG" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsWithEmail(Some("invalidEmail")))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None, None))))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
          mockSessionCacheConnectorPut(HTSSession(None, None, None, None, None))(Right(None))
        }

        val result = getSelectEmailPage()
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.getGiveEmailPage().url)
      }

      "handle DE users with NO email from GG" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsWithEmail(None))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None, None))))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
        }

        val result = getSelectEmailPage()
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.getGiveEmailPage().url)
      }

      "handle DE users with an Missing UserInfo from Auth" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsMissingUserInfo)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None, None))))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
        }

        val result = getSelectEmailPage()
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.getGiveEmailPage().url)
      }

      "handle unexpected errors during enrolment check" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None, None))))
          mockEnrolmentCheck()(Left("unexpected error"))
        }

        val result = getSelectEmailPage()
        status(result) shouldBe 500
      }

      "redirect to NS&I if request comes from already enrolled Digital users with valid email in mongo" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None, None))))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(Some(testEmail)))
        }

        val result = getSelectEmailPage()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("https://nsandi.com")
      }
    }

    "handling selectEmailSubmit requests" must {

        def selectEmailSubmit(newEmail: Option[String]): Future[Result] = {
          newEmail.fold(
            controller.selectEmailSubmit()(fakeRequestWithCSRFToken.withFormUrlEncodedBody("email" → "Yes"))
          ) { e ⇒
              controller.selectEmailSubmit()(fakeRequestWithCSRFToken.withFormUrlEncodedBody("email" → "No", "new-email" → e))
            }
        }

      "handle Digital(new applicant) users with no valid session in keystore" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionCacheConnectorGet(Right(None))
          mockEnrolmentCheck()(Right(NotEnrolled))
        }

        val result = selectEmailSubmit(None)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility().url)
      }

      "handle errors during session cache lookup in keystore" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionCacheConnectorGet(Left("unexpected error"))
        }

        val result = selectEmailSubmit(None)
        status(result) shouldBe 500
      }

      "handle Digital(new applicant) who submitted form with no new-email but with checked existing email" in {
        val session = HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, None)
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionCacheConnectorGet(Right(Some(session)))
          mockEnrolmentCheck()(Right(NotEnrolled))
          mockEncrypt(emailStr)(encryptedEmail)
        }

        val result = selectEmailSubmit(None)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some("/help-to-save/confirm-email/encrypted")
      }

      "handle Digital(new applicant) who submitted form with new-email" in {

        val userInfo = randomEligibleWithUserInfo(validUserInfo)
        val session = HTSSession(Some(Right(userInfo)), None, None, None, None, Some(BankDetails("1", "1", None, "name")))

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionCacheConnectorGet(Right(Some(session)))
          mockEnrolmentCheck()(Right(NotEnrolled))
          mockSessionCacheConnectorPut(session.copy(pendingEmail = Some(testEmail)))(Right(None))
        }

        val result = selectEmailSubmit(Some(testEmail))
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.verifyEmail().url)
      }

      "handle existing digital account holders and redirect them to nsi" in {
        val userInfo = randomEligibleWithUserInfo(validUserInfo)
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(userInfo)), Some("email"), None))))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(Some("email")))
        }
        val result = selectEmailSubmit(Some(testEmail))
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some("https://nsandi.com")
      }

      "handle DE users - throw server error if no existing session found" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionCacheConnectorGet(Right(None))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
        }

        val result = selectEmailSubmit(None)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility().url)
      }

      "handle DE users - throw server error if there is an existing session but no email" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None, None))))
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
          mockSessionCacheConnectorGet(Right(Some(session)))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
          mockEncrypt("email@gmail.com")(encryptedEmail)
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
          mockSessionCacheConnectorGet(Right(Some(session)))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
        }

        val result = selectEmailSubmit(Some(testEmail))
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.verifyEmail().url)
      }

      "handle DE user who submitted form with errors" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None, Some(testEmail)))))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
        }

        val result = selectEmailSubmit(Some("invalidEmail"))
        status(result) shouldBe 200
        contentAsString(result) should include("Which email address do you want to use for your Help to Save account?")
      }

      "handle an existing account holder who submitted form with no new-email but with checked existing email" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsWithEmail(None))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None, Some(testEmail)))))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(Some(testEmail)))
        }

        val result = selectEmailSubmit(None)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some("https://nsandi.com")
      }
    }

    "handling getGiveEmailPage requests" must {
        def getGiveEmailPage(): Future[Result] = controller.getGiveEmailPage(fakeRequestWithCSRFToken)

      "handle Digital(new applicant) users with an existing valid email from GG but not gone through eligibility checks" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionCacheConnectorGet(Right(None))
          mockEnrolmentCheck()(Right(NotEnrolled))
        }

        val result = getGiveEmailPage()
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility().url)
      }

      "handle Digital(new applicant) users with an existing valid email from GG and already gone through eligibility checks" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, None))))
          mockEnrolmentCheck()(Right(NotEnrolled))
        }

        val result = getGiveEmailPage()
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.getSelectEmailPage().url)
      }

      "handle Digital(new applicant) users with an existing INVALID email from GG and should display giveEmailPage" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo).withEmail(Some("invalidEmail")))), None, None))))
          mockEnrolmentCheck()(Right(NotEnrolled))
        }

        val result = getGiveEmailPage()
        status(result) shouldBe 200
        contentAsString(result) should include("Which email address do you want to use for Help to Save?")
      }

      "use correct back link for digital applicants when they come from check details page" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo).withEmail(Some("invalidEmail")))), None, None, None, None, None, true))))
          mockEnrolmentCheck()(Right(NotEnrolled))
        }

        val result = getGiveEmailPage()
        status(result) shouldBe 200
        contentAsString(result) should include("Which email address do you want to use for Help to Save?")
        contentAsString(result) should include("/help-to-save/check-details")
      }

      "handle existing digital account holders and redirect them to nsi" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionCacheConnectorGet(Right(None))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(Some("email")))
        }

        val result = getGiveEmailPage()
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some("https://nsandi.com")
      }

      "handle DE users with an existing valid email from GG" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionCacheConnectorGet(Right(None))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
          mockSessionCacheConnectorPut(HTSSession(None, None, Some("tyrion_lannister@gmail.com"), None, None))(Right(None))
        }

        val result = getGiveEmailPage()
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.getSelectEmailPage().url)
      }

      "DE users should not contain any Back link" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsWithEmail(None))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None, None))))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
          mockSessionCacheConnectorPut(HTSSession(None, None, None))(Right(None))
        }

        val result = getGiveEmailPage()
        status(result) shouldBe 200
        contentAsString(result) should include("Which email address do you want to use for Help to Save?")
      }

      "handle DE users with an existing INVALID email from GG" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsWithEmail(Some("invalidEmail")))
          mockSessionCacheConnectorGet(Right(None))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
          mockSessionCacheConnectorPut(HTSSession(None, None, None, None, None))(Right(None))
        }

        val result = getGiveEmailPage()
        status(result) shouldBe 200
        contentAsString(result) should include("Which email address do you want to use for Help to Save?")
      }

      "handle DE users with NO email from GG" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsWithEmail(None))
          mockSessionCacheConnectorGet(Right(None))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
          mockSessionCacheConnectorPut(HTSSession(None, None, None, None, None))(Right(None))
        }

        val result = getGiveEmailPage()
        status(result) shouldBe 200
        contentAsString(result) should include("Which email address do you want to use for Help to Save?")
      }

      "handle DE users with an Missing UserInfo from Auth" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsMissingUserInfo)
          mockSessionCacheConnectorGet(Right(None))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
          mockSessionCacheConnectorPut(HTSSession(None, None, None, None, None))(Right(None))
        }

        val result = getGiveEmailPage()
        status(result) shouldBe 200
        contentAsString(result) should include("Which email address do you want to use for Help to Save?")
      }

      "handle unexpected errors during enrolment check" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionCacheConnectorGet(Right(None))
          mockEnrolmentCheck()(Left("unexpected error"))
        }

        val result = getGiveEmailPage()
        status(result) shouldBe 500
      }

      "redirect to NS&I if request comes from already enrolled Digital users with valid email in mongo" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionCacheConnectorGet(Right(None))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(Some(testEmail)))
        }

        val result = getGiveEmailPage()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("https://nsandi.com")
      }

    }

    "handling giveEmailSubmit requests" must {

      val email = "email@test.com"

        def giveEmailSubmit(email: String): Future[Result] = controller.giveEmailSubmit()(
          fakeRequestWithCSRFToken.withFormUrlEncodedBody("email" → email))

      "handle Digital(new applicant) users with no valid session in keystore" in {

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionCacheConnectorGet(Right(None))
          mockEnrolmentCheck()(Right(NotEnrolled))
        }

        val result = giveEmailSubmit(email)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility().url)
      }

      "handle errors during session cache lookup in keystore" in {

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionCacheConnectorGet(Left("unexpected error"))
        }

        val result = giveEmailSubmit(email)
        status(result) shouldBe 500
      }

      "handle Digital(new applicant) who submitted form with new email" in {
        val userInfo = randomEligibleWithUserInfo(validUserInfo)
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(userInfo)), None, None, None, None, Some(BankDetails("1", "1", None, "name"))))))
          mockEnrolmentCheck()(Right(NotEnrolled))
          mockSessionCacheConnectorPut(HTSSession(Some(Right(userInfo)), None, Some(email), None, None, Some(BankDetails("1", "1", None, "name"))))(Right(None))
        }

        val result = giveEmailSubmit(email)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.verifyEmail().url)
      }

      "handle existing digital account holders and redirect them to nsi" in {
        val userInfo = randomEligibleWithUserInfo(validUserInfo)
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(userInfo)), None, Some(testEmail)))))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(Some("email")))
        }
        val result = giveEmailSubmit(testEmail)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some("https://nsandi.com")
      }

      "handle DE users - redirect to check eligibility if no existing session found" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionCacheConnectorGet(Right(None))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
        }

        val result = giveEmailSubmit(email)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility().url)
      }

      "handle DE user who submitted form with new-email" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None, None))))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
          mockSessionCacheConnectorPut(HTSSession(None, None, Some(email)))(Right(()))
        }

        val result = giveEmailSubmit(email)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.verifyEmail().url)
      }

      "handle DE user who submitted form with errors" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None, Some(testEmail)))))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
        }

        val result = giveEmailSubmit("badEmail")
        status(result) shouldBe 200
        contentAsString(result) should include("Which email address do you want to use for Help to Save?")
      }
    }

    "handling confirmEmail requests" must {

      val email = "test@user.com"
      val userInfo = randomEligibleWithUserInfo(validUserInfo)
      val userInfoWithInvalidEmail = randomEligibleWithUserInfo(validUserInfo).withEmail(Some("invalidEmail"))

        def confirmEmail(encryptedEmail: String): Future[Result] = controller.confirmEmail(encryptedEmail)(fakeRequestWithCSRFToken)

      "handle Digital(new applicant) users with an existing valid email from GG but not gone through eligibility checks" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockDecrypt("encrypted")("decrypted")
          mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None, None))))
          mockEnrolmentCheck()(Right(NotEnrolled))
        }

        val result = confirmEmail(encryptedEmail)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility().url)
      }

      "handle Digital(new applicant) users with an existing valid email from GG, already gone through eligibility checks and no bank details in session" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockDecrypt("encrypted")("decrypted")
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(userInfo)), None, None))))
          mockEnrolmentCheck()(Right(NotEnrolled))
          mockSessionCacheConnectorPut(HTSSession(Some(Right(userInfo)), Some("decrypted"), None))(Right(None))
          mockStoreConfirmedEmail("decrypted")(Right(None))
        }

        val result = confirmEmail(encryptedEmail)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.BankAccountController.getBankDetailsPage().url)
      }

      "handle Digital(new applicant) users with an existing valid email from GG, already gone through eligibility checks but bank details are already in session" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockDecrypt("encrypted")("decrypted")
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(userInfo)), None, None, None, None, Some(BankDetails("1", "1", Some("1"), "a")), true))))
          mockEnrolmentCheck()(Right(NotEnrolled))
          mockSessionCacheConnectorPut(HTSSession(Some(Right(userInfo)), Some("decrypted"), None, None, None, Some(BankDetails("1", "1", Some("1"), "a")), true))(Right(None))
          mockStoreConfirmedEmail("decrypted")(Right(None))
        }

        val result = confirmEmail(encryptedEmail)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.RegisterController.checkDetails().url)
      }

      "handle Digital(new applicant) users with an existing INVALID email from GG and already gone through eligibility checks" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockDecrypt("encrypted")("decrypted")
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(userInfoWithInvalidEmail)), None, None))))
          mockEnrolmentCheck()(Right(NotEnrolled))
        }

        val result = confirmEmail(encryptedEmail)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.getGiveEmailPage().url)
      }

      "handle existing digital account holders and redirect them to nsi" in {
        val newEmail = "email@hmrc.com"
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockDecrypt("encrypted")("decrypted")
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), Some(newEmail), None))))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(Some("email@email.com")))
        }

        val result = confirmEmail(encryptedEmail)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some("https://nsandi.com")
      }

      "handle DE users with an existing valid email from GG" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockDecrypt("encrypted")("decrypted")
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(userInfo)), None, Some(email)))))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
          mockSessionCacheConnectorPut(HTSSession(None, Some("decrypted"), None))(Right(None))
          mockStoreConfirmedEmail("decrypted")(Right(None))
          mockUpdateEmail(NSIPayload(userInfo.userInfo.copy(email = Some("decrypted")), "decrypted", version, systemId))(Right(None))
          mockAudit(EmailChanged(nino, "", "decrypted", false, routes.EmailController.confirmEmail(encryptedEmail).url))
        }

        val result = confirmEmail(encryptedEmail)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some("https://nsandi.com")
      }
    }

    "handling emailVerifiedCallback requests" must {

        def emailVerifiedCallback(emailVerificationParams: String): Future[Result] =
          controller.emailVerifiedCallback(emailVerificationParams)(fakeRequestWithCSRFToken)

      val email = "test@user.com"

      val encryptedParams = new String(Base64.getEncoder.encode("encrypted".getBytes))

      val eligibleWithUserInfo = randomEligibleWithUserInfo(validUserInfo.copy(email = Some(email), nino = nino))

      "handle Digital users and return success result" in {
        val newEmail = "new@email.com"

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockDecrypt("encrypted")(s"$nino#$newEmail")
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithUserInfo)), None, None, changingDetails = true))))
          mockSessionCacheConnectorPut(HTSSession(Some(Right(eligibleWithUserInfo.withEmail(Some(newEmail)))), Some(newEmail), None, changingDetails = true))(Right(None))
          mockStoreConfirmedEmail(newEmail)(Right(None))
          mockAudit(EmailChanged(nino, email, newEmail, true, routes.EmailController.emailVerifiedCallback(encryptedParams).url))
        }

        val result = emailVerifiedCallback(encryptedParams)

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.getEmailVerified().url)

      }

      "handle Digital users and return success result when there is no GG email" in {
        val newEmail = "new@email.com"

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsWithEmail(None))
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockDecrypt("encrypted")(s"$nino#$newEmail")
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithUserInfo.withEmail(None))), None, None))))
          mockSessionCacheConnectorPut(HTSSession(Some(Right(eligibleWithUserInfo.withEmail(Some(newEmail)))), Some(newEmail), None))(Right(None))
          mockStoreConfirmedEmail(newEmail)(Right(None))
          mockAudit(EmailChanged(nino, "", newEmail, true, routes.EmailController.emailVerifiedCallback(encryptedParams).url))
        }

        val result = emailVerifiedCallback(encryptedParams)

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.getEmailVerified().url)

      }

      "handle Digital users and return server error when NINOs do not match" in {

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockDecrypt("encrypted")("AE123456C#test@user.com")
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithUserInfo)), None, None))))
        }

        val result = emailVerifiedCallback(encryptedParams)

        status(result) shouldBe 500
      }

      "handle Digital errors when email verification params CANNOT be decoded" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockAudit(SuspiciousActivity(Some(nino), "malformed_redirect", routes.EmailController.emailVerifiedCallback("blah blah").url))
        }

        val result = emailVerifiedCallback("blah blah")

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.verifyEmailErrorTryLater().url)
      }

      "handle Digital users who have not gone through eligibility checks and are eligible" in {
        val newEmail = "new@email.com"

        val eligibilityResult = randomEligibleWithUserInfo(validUserInfo.copy(email = Some(email)))

        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockDecrypt("encrypted")(s"$nino#$newEmail")
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibilityResult)), Some(email), None))))
          mockSessionCacheConnectorPut(HTSSession(Some(Right(eligibilityResult.withEmail(Some(newEmail)))), Some(newEmail), None))(Right(None))
          mockStoreConfirmedEmail(newEmail)(Right(None))
          mockAudit(EmailChanged(nino, email, newEmail, true, routes.EmailController.emailVerifiedCallback(encryptedParams).url))
        }

        val result = emailVerifiedCallback(encryptedParams)

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.getEmailVerified().url)
      }

      "handle Digital users who have not gone through eligibility checks and not eligible" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockDecrypt("encrypted")("WM123456C#test@user.com")
          mockSessionCacheConnectorGet(Right(Some(HTSSession(None, Some(email), None))))
          mockEligibilityResult()(Right(EligibilityCheckResult.Ineligible(randomEligibilityResponse())))
        }

        val result = emailVerifiedCallback(encryptedParams)

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsNotEligible().url)
      }

      "handle unexpected errors during enrolment check" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck()(Left("error"))
        }

        val result = emailVerifiedCallback(encryptedParams)

        status(result) shouldBe 500
      }

      "handle DE users with missing user info" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsMissingUserInfo)
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
        }

        val result = emailVerifiedCallback(encryptedParams)

        status(result) shouldBe 500
      }

      "handle DE users and update email successfully with NS&I" in {

        val updatedNSIPayload = NSIPayload(validUserInfo.copy(email = Some(email)), email, version, systemId)
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
          mockDecrypt("encrypted")(s"WM123456C#$email")
          mockUpdateEmail(updatedNSIPayload)(Right(None))
          mockStoreConfirmedEmail(email)(Right(None))
          mockSessionCacheConnectorPut(HTSSession(None, Some(email), None))(Right(None))
          mockAudit(EmailChanged(nino, "", email, false, routes.EmailController.emailVerifiedCallback(encryptedParams).url))
        }

        val result = emailVerifiedCallback(encryptedParams)

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.getEmailVerified().url)
      }

      "handle DE users and handle errors during updating email with NS&I" in {

        val updatedNSIUserInfo = NSIPayload(validUserInfo.copy(email = Some(email)), email, version, systemId)
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
          mockDecrypt("encrypted")("WM123456C#test@user.com")
          mockUpdateEmail(updatedNSIUserInfo)(Left("error"))
        }

        val result = emailVerifiedCallback(encryptedParams)
        status(result) shouldBe 500
      }

      "handle DE errors when email verification params CANNOT be decoded" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
          mockAudit(SuspiciousActivity(Some(nino), "malformed_redirect", routes.EmailController.emailVerifiedCallback("blah blah").url))
        }

        val result = emailVerifiedCallback("blah blah")

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.verifyEmailErrorTryLater().url)
      }

    }

    "handling verifyEmail requests" must {
        def verifyEmail: Future[Result] =
          controller.verifyEmail(fakeRequestWithCSRFToken)

      "handle Digital users and return the check your email page with a status of Ok" in {

        val newEmail = "email@hmrc.com"
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), None, Some(newEmail)))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockEmailVerification(nino, newEmail, firstName)(Right(()))
        }

        val result = verifyEmail
        status(result) shouldBe 200
        contentAsString(result).contains(messagesApi("hts.email-verification.check-your-email.title.h1")) shouldBe true
        contentAsString(result).contains(messagesApi("hts.email-verification.check-your-email.content2")) shouldBe true
      }

      "handle Digital users who have not gone through eligibility checks" in {
        val newEmail = "email@hmrc.com"
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None, Some(newEmail)))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        }

        val result = verifyEmail
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility().url)
      }

      "handle Digital users who have already verified their email" in {
        val newEmail = "email@hmrc.com"
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), None, Some(newEmail)))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockEmailVerification(nino, newEmail, firstName)(Left(AlreadyVerified))
          mockEncrypt("WM123456C#email@hmrc.com")("decrypted")
        }

        val result = verifyEmail
        status(result) shouldBe 303
        redirectLocation(result).getOrElse("") should include("/help-to-save/email-verified-callback")
      }

      "handle existing digital account holders and redirect them to nsi" in {
        val newEmail = "email@hmrc.com"
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), Some(newEmail), None))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
          mockGetConfirmedEmail()(Right(Some("email@email.com")))
        }

        val result = verifyEmail
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some("https://nsandi.com")
      }

      "handle DE users with pending email in the session" in {
        val newEmail = "email@hmrc.com"
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), None, Some(newEmail)))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
          mockEmailVerification(nino, newEmail, firstName)(Right(()))
        }

        val result = verifyEmail
        status(result) shouldBe 200
        contentAsString(result).contains(messagesApi("hts.email-verification.check-your-email.title.h1")) shouldBe true
        contentAsString(result).contains(messagesApi("hts.email-verification.check-your-email.content2")) shouldBe true
      }

      "handle DE users with missing user info from GG" in {
        val newEmail = "email@hmrc.com"
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsMissingUserInfo)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), Some(newEmail), None))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
        }

        val result = verifyEmail
        status(result) shouldBe 500
      }

      "handle DE users with NO stored pending email in the session" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), None, None))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
        }

        val result = verifyEmail
        status(result) shouldBe 500
      }
    }

    "handling verifyEmailError requests" must {

        def verifyEmailError: Future[Result] =
          controller.verifyEmailError(fakeRequestWithCSRFToken)

      "handle Digital users who are already gone through eligibility checks" in {
        val newEmail = "email@hmrc.com"
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), Some(newEmail), None))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        }

        val result = verifyEmailError
        status(result) shouldBe 200
        contentAsString(result) should include("We cannot change your email address at the moment")
      }

      "handle existing digital account holders and redirect them to nsi" in {
        val newEmail = "email@hmrc.com"
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), Some(newEmail), None))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
          mockGetConfirmedEmail()(Right(Some("email@email.com")))
        }

        val result = verifyEmailError
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some("https://nsandi.com")
      }

      "handle DE users" in {
        val newEmail = "email@hmrc.com"
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), Some(newEmail), None))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
        }

        val result = verifyEmailError
        status(result) shouldBe 200
        contentAsString(result) should include("We cannot change your email address at the moment")
      }

    }

    "handling verifyEmailErrorTryLater requests" must {
        def verifyEmailErrorTryLater: Future[Result] =
          controller.verifyEmailErrorTryLater(fakeRequestWithCSRFToken)

      "handle Digital users who are already gone through eligibility checks" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None, None))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        }

        val result = verifyEmailErrorTryLater
        status(result) shouldBe 200
        contentAsString(result) should include("We cannot change your email")
      }

      "handle DE users" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None, None))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
        }

        val result = verifyEmailErrorTryLater
        status(result) shouldBe 200
        contentAsString(result) should include("We cannot change your email address at the moment")
      }
    }

    "handling verifyEmailErrorSubmit requests" must {

        def verifyEmailErrorSubmit(continue: Boolean): Future[Result] =
          controller.verifyEmailErrorSubmit()(fakeRequestWithCSRFToken.withFormUrlEncodedBody("radio-inline-group" → continue.toString))

      "handle Digital users and redirect to the email verify error page try later if there is no email for the user" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo.copy(email = None)))), None, None))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        }

        val result = verifyEmailErrorSubmit(true)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.verifyEmailErrorTryLater().url)
      }

      "handle Digital users and redirect to the confirmEmail endpoint if there is an email for the user and the user selects to continue" in {

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), None, None))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockEncrypt(emailStr)(encryptedEmail)
        }

        val result = verifyEmailErrorSubmit(true)
        status(result) shouldBe 303

        redirectLocation(result) shouldBe Some(routes.EmailController.confirmEmail(encryptedEmail).url)
      }

      "handle Digital users and redirect to the info endpoint if there is an email for the user and the user selects not to continue" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), None, None))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        }

        val result = verifyEmailErrorSubmit(false)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.IntroductionController.getAboutHelpToSave().url)
      }

      "handle Digital users and show the verify email error page again if there is an error in the form" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), None, None))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        }

        val result = controller.verifyEmailErrorSubmit()(fakeRequestWithCSRFToken)
        status(result) shouldBe 200
        contentAsString(result) should include("We cannot change your email address at the moment")
      }

      "handle existing digital account holders and redirect them to nsi" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None, None))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
          mockGetConfirmedEmail()(Right(Some("email@email.com")))
        }

        val result = controller.verifyEmailErrorSubmit()(fakeRequestWithCSRFToken)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some("https://nsandi.com")
      }

      "handle DE users and redirect to the confirmEmail endpoint if there is an email for the user and the user selects to continue" in {

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(None, Some(emailStr), None))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
          mockEncrypt(emailStr)(encryptedEmail)
        }

        val result = verifyEmailErrorSubmit(true)
        status(result) shouldBe 303

        redirectLocation(result) shouldBe Some(routes.EmailController.confirmEmail(encryptedEmail).url)

      }
    }

    "handling getEmailVerified" must {

        def getEmailVerified = controller.getEmailVerified(fakeRequestWithCSRFToken)

      "handle Digital users and return the email verified page" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), Some("email"), None))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        }

        val result = getEmailVerified
        status(result) shouldBe OK
        contentAsString(result) should include("Email address verified")
      }

      "handle Digital users and redirect to check eligibility" when {

        "there is no session" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockSessionCacheConnectorGet(Right(None))
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          }

          val result = getEmailVerified
          status(result) shouldBe 303
          redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility().url)
        }

      }

      "handle Digital users and return an error" when {

        "there is no confirmed email in the session" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), None, None))))
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          }

          val result = getEmailVerified
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.EmailController.verifyEmailError().url)
        }

        "there is no confirmed email in the session when there is no email for the user" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo.copy(userInfo = validUserInfo.copy(email = None)))), None, None))))
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          }

          val result = getEmailVerified
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.EmailController.verifyEmailErrorTryLater().url)
        }

        "the call to session cache fails" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockSessionCacheConnectorGet(Left(""))
          }

          val result = getEmailVerified
          checkIsTechnicalErrorPage(result)
        }
      }

      "handle DE users and return the give email page" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), Some("email"), None))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
          mockGetConfirmedEmail()(Right(None))
        }

        val result = getEmailVerified
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/help-to-save/enter-email")
      }

    }

    "handling getEmailUpdated" must {

        def getEmailUpdated(): Future[Result] =
          controller.getEmailUpdated()(fakeRequestWithCSRFToken)

      "show the email updated page otherwise" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), None, None))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        }

        val result = getEmailUpdated()
        status(result) shouldBe OK
        contentAsString(result) should include("Email address verified")

      }

    }

    "handling emailUpdatedSubmit" must {

      "handle Digital users and redirect to the getBankDetailsPage if bank details are not already in session" in {

        inSequence{
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), None, None))))
          mockEnrolmentCheck()(Right(NotEnrolled))
        }

        val result = controller.emailUpdatedSubmit()(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.BankAccountController.getBankDetailsPage().url)
      }

      "handle Digital users and redirect to the checkDetailsPage if the user is in the process of changing details" in {

        inSequence{
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), None, None, None, None, Some(BankDetails("1", "1", None, "name")),
                                                             changingDetails = true))))
          mockEnrolmentCheck()(Right(NotEnrolled))
        }

        val result = controller.emailUpdatedSubmit()(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.RegisterController.checkDetails().url)
      }

      "handle Digital users and redirect to the eligibilityCheck if session doesnt contain eligibility result" in {

        inSequence{
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionCacheConnectorGet(Right(None))
          mockEnrolmentCheck()(Right(NotEnrolled))
        }

        val result = controller.emailUpdatedSubmit()(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility().url)
      }

      "handle existing digital account holders and redirect them to NSI" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), Some("email"), None))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
          mockGetConfirmedEmail()(Right(Some("email")))
        }

        val result = controller.emailUpdatedSubmit()(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("https://nsandi.com")
      }

      "handle DE users and redirect to the give email page" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), None, None))))
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
