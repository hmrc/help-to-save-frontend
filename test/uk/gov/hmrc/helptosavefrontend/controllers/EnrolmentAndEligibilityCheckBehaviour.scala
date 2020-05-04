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

import cats.data.EitherT
import cats.instances.future._
import play.api.mvc.Result
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithCL200
import uk.gov.hmrc.helptosavefrontend.models.{EnrolmentStatus, HTSSession}
import uk.gov.hmrc.helptosavefrontend.models.account.AccountNumber
import uk.gov.hmrc.helptosavefrontend.services.{HelpToSaveReminderService, HelpToSaveService}
import uk.gov.hmrc.helptosavefrontend.connectors.HelpToSaveConnector
import uk.gov.hmrc.helptosavefrontend.forms.{BankDetails, SortCode}
import uk.gov.hmrc.helptosavefrontend.models.TestData.Eligibility.randomEligibleWithUserInfo
import uk.gov.hmrc.helptosavefrontend.models.TestData.UserData.{validNSIPayload, validUserInfo}
import uk.gov.hmrc.helptosavefrontend.models.register.CreateAccountRequest
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveServiceImpl.{SubmissionFailure, SubmissionSuccess}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait EnrolmentAndEligibilityCheckBehaviour {
  this: ControllerSpecWithGuiceApp with AuthSupport with SessionStoreBehaviourSupport ⇒

  val mockHelpToSaveService = mock[HelpToSaveService]

  val mockHelpToSaveConnector = mock[HelpToSaveConnector]

  val mockHelpToSaveReminderService = mock[HelpToSaveReminderService]

  val confirmedEmail = "confirmed"
  val bankDetails = BankDetails(SortCode(1, 2, 3, 4, 5, 6), "1", None, "name")
  val userInfo = randomEligibleWithUserInfo(validUserInfo)
  val payload = validNSIPayload
    .updateEmail(confirmedEmail)
    .copy(nbaDetails = Some(bankDetails))
    .copy(version = "V2.0")
    .copy(systemId = "MDTP REGISTRATION")

  val accountNumber = "1234567890123"

  val createAccountRequest = CreateAccountRequest(payload, userInfo.eligible.value.eligibilityCheckResult.reasonCode)

  def mockEnrolmentCheck()(result: Either[String, EnrolmentStatus]): Unit =
    (mockHelpToSaveService
      .getUserEnrolmentStatus()(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *)
      .returning(EitherT.fromEither[Future](result))

  def mockWriteITMPFlag(result: Option[Either[String, Unit]]): Unit =
    (mockHelpToSaveService
      .setITMPFlagAndUpdateMongo()(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *)
      .returning(
        result
          .fold(EitherT.liftF[Future, String, Unit](Future.failed(new Exception)))(r ⇒ EitherT.fromEither[Future](r))
      )

  def mockWriteITMPFlag(result: Either[String, Unit]): Unit =
    mockWriteITMPFlag(Some(result))

  def mockCreateAccount(
    createAccountRequest: CreateAccountRequest
  )(response: Either[SubmissionFailure, SubmissionSuccess]): Unit =
    (mockHelpToSaveService
      .createAccount(_: CreateAccountRequest)(_: HeaderCarrier, _: ExecutionContext))
      .expects(createAccountRequest, *, *)
      .returning(EitherT.fromEither[Future](response))

  def mockGetAccountNumber()(result: Either[String, AccountNumber]): Unit =
    (mockHelpToSaveConnector
      .getAccountNumber()(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *)
      .returning(EitherT.fromEither[Future](result))

  def mockGetAccountNumberFromService()(result: Either[String, AccountNumber]): Unit =
    (mockHelpToSaveService
      .getAccountNumber()(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *)
      .returning(EitherT.fromEither[Future](result))

  def commonEnrolmentAndSessionBehaviour(
    getResult: () ⇒ Future[Result], // scalastyle:ignore method.length
    mockSuccessfulAuth: () ⇒ Unit = () ⇒ mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval),
    mockNoNINOAuth: () ⇒ Unit = () ⇒ mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(None),
    testRedirectOnNoSession: Boolean = true,
    testEnrolmentCheckError: Boolean = true,
    testRedirectOnNoEligibilityCheckResult: Boolean = true
  ): Unit = {

    "redirect to NS&I if the user is already enrolled" in {
      inSequence {
        mockSuccessfulAuth()
        mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(itmpHtSFlag = true)))
      }

      val result = getResult()
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(appConfig.nsiManageAccountUrl)
    }

    "redirect to NS&I if the user is already enrolled and set the ITMP flag " +
      "if it has not already been set" in {
      inSequence {
        mockSuccessfulAuth()
        mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(itmpHtSFlag = false)))
        mockWriteITMPFlag(Right(()))
      }

      val result = getResult()
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(appConfig.nsiManageAccountUrl)
    }

    "redirect to NS&I if the user is already enrolled even if there is an " +
      "setting the ITMP flag" in {
      def test(mockActions: ⇒ Unit): Unit = {
        mockActions
        val result = getResult()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(appConfig.nsiManageAccountUrl)
      }

      test(inSequence {
        mockSuccessfulAuth()
        mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(itmpHtSFlag = false)))
        mockWriteITMPFlag(Left(""))
      })

      test(inSequence {
        mockSuccessfulAuth()
        mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(itmpHtSFlag = false)))
        mockWriteITMPFlag(None)
      })
    }

    if (testRedirectOnNoSession) {
      "redirect to the eligibility checks if there is no session data for the user" in {
        inSequence {
          mockSuccessfulAuth()
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(Right(None))
        }

        val result = getResult()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility().url)
      }
    }

    if (testRedirectOnNoEligibilityCheckResult) {
      "redirect to the eligibility checks if there is no eligibility check result in the session data for the user" in {
        inSequence {
          mockSuccessfulAuth()
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(Right(Some(HTSSession(None, None, None))))
        }

        val result = getResult()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility().url)
      }
    }

    "return an error" when {

      if (testEnrolmentCheckError) {
        "there is an error getting the enrolment status" in {
          inSequence {
            mockSuccessfulAuth()
            mockEnrolmentCheck()(Left(""))
          }
          checkIsTechnicalErrorPage(getResult())
        }
      }

      "there is no NINO returned by auth" in {
        mockNoNINOAuth()

        checkIsTechnicalErrorPage(getResult())
      }

      "there is an error getting the session data" in {
        inSequence {
          mockSuccessfulAuth()
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(Left(""))
        }

        checkIsTechnicalErrorPage(getResult())
      }

    }
  }

}
