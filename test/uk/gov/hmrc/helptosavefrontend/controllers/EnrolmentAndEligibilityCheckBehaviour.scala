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
import org.mockito.Mockito.when
import play.api.mvc.Result
import play.api.test.Helpers.*
import uk.gov.hmrc.auth.core.syntax.retrieved.authSyntaxForRetrieved
import uk.gov.hmrc.helptosavefrontend.connectors.{HelpToSaveConnector, HelpToSaveReminderConnector}
import uk.gov.hmrc.helptosavefrontend.models.{BankDetails, SortCode}
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithCL200
import uk.gov.hmrc.helptosavefrontend.models.TestData.Eligibility.randomEligibleWithUserInfo
import uk.gov.hmrc.helptosavefrontend.models.TestData.UserData.{validNSIPayload, validUserInfo}
import uk.gov.hmrc.helptosavefrontend.models.account.{Account, AccountNumber}
import uk.gov.hmrc.helptosavefrontend.models.register.CreateAccountRequest
import uk.gov.hmrc.helptosavefrontend.models.reminder.{CancelHtsUserReminder, HtsUserSchedule}
import uk.gov.hmrc.helptosavefrontend.models.{EnrolmentStatus, HTSSession}
import uk.gov.hmrc.helptosavefrontend.models.SubmissionResult
import uk.gov.hmrc.helptosavefrontend.models.SubmissionResult.{SubmissionFailure, SubmissionSuccess}
import uk.gov.hmrc.helptosavefrontend.services.{HelpToSaveReminderService, HelpToSaveService}

import scala.concurrent.Future

trait EnrolmentAndEligibilityCheckBehaviour {
  this: ControllerSpecWithGuiceApp with AuthSupport with SessionStoreBehaviourSupport =>

  val mockHelpToSaveService = mock[HelpToSaveService]

  val mockHelpToSaveConnector = mock[HelpToSaveConnector]

  val mockHelpToSaveReminderService = mock[HelpToSaveReminderService]

  val mockHelpToSaveReminderConnector = mock[HelpToSaveReminderConnector]

  val confirmedEmail = "confirmed"
  val bankDetails = BankDetails("name", SortCode(1, 2, 3, 4, 5, 6), "1", None)
  val userInfo = randomEligibleWithUserInfo(validUserInfo)
  val payload = validNSIPayload
    .updateEmail(confirmedEmail)
    .copy(nbaDetails = Some(bankDetails))
    .copy(version = "V2.0")
    .copy(systemId = "MDTP REGISTRATION")

  val accountNumber = "1234567890123"

  val nino2 = "WM123456C"

  val createAccountRequest = CreateAccountRequest(payload, userInfo.eligible.value.eligibilityCheckResult.reasonCode)

  val cancelHtsUserReminder = CancelHtsUserReminder(nino2)

  def mockEnrolmentCheck()(result: Either[String, EnrolmentStatus]): Unit =
    when(mockHelpToSaveService.getUserEnrolmentStatus()(any(), any())).thenReturn(EitherT.fromEither[Future](result))

  def mockWriteITMPFlag(result: Option[Either[String, Unit]]): Unit =
    when(mockHelpToSaveService.setITMPFlagAndUpdateMongo()(any(), any())).thenReturn(
      result
        .fold(EitherT.liftF[Future, String, Unit](Future.failed(new Exception)))(r => EitherT.fromEither[Future](r))
    )

  def mockGetAccount()(result: Either[String, Account]): Unit =
    when(mockHelpToSaveService.getAccount(eqTo(nino), any())(any(), any()))
      .thenReturn(EitherT.fromEither[Future](result))

  def mockWriteITMPFlag(result: Either[String, Unit]): Unit =
    mockWriteITMPFlag(Some(result))

  def mockCreateAccount(
    createAccountRequest: CreateAccountRequest
  )(response: Either[SubmissionFailure, SubmissionSuccess]): Unit =
    when(mockHelpToSaveService.createAccount(eqTo(createAccountRequest))(any(), any()))
      .thenReturn(EitherT.fromEither[Future](response))

  def mockGetAccountNumber()(result: Either[String, AccountNumber]): Unit =
    when(mockHelpToSaveConnector.getAccountNumber()(any(), any())).thenReturn(EitherT.fromEither[Future](result))

  def mockGetAccountNumberFromService()(result: Either[String, AccountNumber]): Unit =
    when(mockHelpToSaveService.getAccountNumber()(any(), any())).thenReturn(EitherT.fromEither[Future](result))

  def mockGetHtsUserReminders(nino: String)(result: Either[String, HtsUserSchedule]): Unit =
    when(mockHelpToSaveReminderConnector.getHtsUser(eqTo(nino))(any(), any()))
      .thenReturn(EitherT.fromEither[Future](result))

  def mockCancelHtsUserReminders(cancelHtsUserReminder: CancelHtsUserReminder)(result: Either[String, Unit]): Unit =
    when(mockHelpToSaveReminderConnector.cancelHtsUserReminders(eqTo(cancelHtsUserReminder))(any(), any())).thenReturn(
      EitherT
        .fromEither[Future](result)
    )

  def commonEnrolmentAndSessionBehaviour(
    getResult: () => Future[Result], // scalastyle:ignore method.length
    mockSuccessfulAuth: () => Unit = () =>
      mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment),
    mockNoNINOAuth: () => Unit = () =>
      mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(None and noPersonalTaxEnrolment),
    testRedirectOnNoSession: Boolean = true,
    testEnrolmentCheckError: Boolean = true,
    testRedirectOnNoEligibilityCheckResult: Boolean = true
  ): Unit = {

    "redirect to NS&I if the user is already enrolled" in {
      mockSuccessfulAuth()
      mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(itmpHtSFlag = true)))

      val result = getResult()
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(appConfig.nsiManageAccountUrl)
    }

    "redirect to NS&I if the user is already enrolled and set the ITMP flag " +
      "if it has not already been set" in {
      mockSuccessfulAuth()
      mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(itmpHtSFlag = false)))
      mockWriteITMPFlag(Right(()))

      val result = getResult()
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(appConfig.nsiManageAccountUrl)
    }

    "redirect to NS&I if the user is already enrolled even if there is an " +
      "setting the ITMP flag" in {
      def test(mockActions: => Unit): Unit = {
        mockActions
        val result = getResult()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(appConfig.nsiManageAccountUrl)
      }

      test({
        mockSuccessfulAuth()
        mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(itmpHtSFlag = false)))
        mockWriteITMPFlag(Left(""))
      })

      test({
        mockSuccessfulAuth()
        mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(itmpHtSFlag = false)))
        mockWriteITMPFlag(None)
      })
    }

    if (testRedirectOnNoSession) {
      "redirect to the eligibility checks if there is no session data for the user" in {
        mockSuccessfulAuth()
        mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        mockSessionStoreGet(Right(None))

        val result = getResult()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility.url)
      }
    }

    if (testRedirectOnNoEligibilityCheckResult) {
      "redirect to the eligibility checks if there is no eligibility check result in the session data for the user" in {
        mockSuccessfulAuth()
        mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        mockSessionStoreGet(Right(Some(HTSSession(None, None, None))))

        val result = getResult()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility.url)
      }
    }

    "return an error" when {

      if (testEnrolmentCheckError) {
        "there is an error getting the enrolment status" in {
          mockSuccessfulAuth()
          mockEnrolmentCheck()(Left(""))
          checkIsTechnicalErrorPage(getResult())
        }
      }

      "there is no NINO returned by auth" in {
        mockNoNINOAuth()

        checkIsTechnicalErrorPage(getResult())
      }

      "there is an error getting the session data" in {
        mockSuccessfulAuth()
        mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        mockSessionStoreGet(Left(""))

        checkIsTechnicalErrorPage(getResult())
      }

    }
  }

}
