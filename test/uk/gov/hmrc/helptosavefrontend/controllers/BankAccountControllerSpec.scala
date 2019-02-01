/*
 * Copyright 2019 HM Revenue & Customs
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
import play.api.http.Status
import play.api.mvc.Result
import cats.instances.future._
import play.api.test.Helpers.{contentAsString, _}
import uk.gov.hmrc.helptosavefrontend.forms.{BankDetails, BankDetailsValidation, SortCode}
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithCL200
import uk.gov.hmrc.helptosavefrontend.models.TestData.Eligibility.{randomEligibility, randomEligibleWithUserInfo, randomIneligibility}
import uk.gov.hmrc.helptosavefrontend.models.TestData.UserData.validUserInfo
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityCheckResponse
import uk.gov.hmrc.helptosavefrontend.models.{EnrolmentStatus, HTSSession, ValidateBankDetailsRequest, ValidateBankDetailsResult}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class BankAccountControllerSpec extends AuthSupport
  with CSRFSupport
  with EnrolmentAndEligibilityCheckBehaviour
  with SessionStoreBehaviourSupport {

  implicit lazy val bankDetailsValidation: BankDetailsValidation = new BankDetailsValidation(appConfig)

  def mockValidateBankDetails(request: ValidateBankDetailsRequest)(response: Either[String, ValidateBankDetailsResult]) =
    (mockHelpToSaveService.validateBankDetails(_: ValidateBankDetailsRequest)(_: HeaderCarrier, _: ExecutionContext))
      .expects(request, *, *)
      .returning(EitherT.fromEither[Future](response))

  val controller = new BankAccountController(
    mockHelpToSaveService,
    mockSessionStore,
    mockAuthConnector,
    mockMetrics)

  "The BankAccountController" when {

    "handling getBankDetailsPage" must {

        def doRequest() = controller.getBankDetailsPage()(fakeRequestWithCSRFToken)

      doCommonChecks(doRequest)

      "handle the request and display the page" in {

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, None))))
        }

        val result = doRequest()
        status(result) shouldBe Status.OK
        contentAsString(result) should include("Enter the UK bank account details you want us to pay your bonuses and transfer withdrawals into")

      }

      "display the page with correct Back link when they came from SelectEmail page" in {

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, None))))
        }

        val result = doRequest()
        status(result) shouldBe Status.OK
        contentAsString(result) should include("Enter the UK bank account details you want us to pay your bonuses and transfer withdrawals into")
        contentAsString(result) should include("/help-to-save/select-email")
      }

      "display the page with correct Back link when they came from emailVerified page" in {

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, Some("pendingEmail")))))
        }

        val result = doRequest()
        status(result) shouldBe Status.OK
        contentAsString(result) should include("Enter the UK bank account details you want us to pay your bonuses and transfer withdrawals into")
        contentAsString(result) should include("/help-to-save/email-confirmed")
      }

      "display the page with correct Back link when they came from check details page" in {

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, None, None, None, None, true))))
        }

        val result = doRequest()
        status(result) shouldBe Status.OK
        contentAsString(result) should include("Enter the UK bank account details you want us to pay your bonuses and transfer withdrawals into")
        contentAsString(result) should include("/help-to-save/create-account")

      }

      "send already stored bank details in the session back to the UI" in {

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, None, None, None, Some(BankDetails(SortCode(1, 2, 3, 4, 5, 6), "accountNumber", Some(""), "accountName"))))))
        }

        val result = doRequest()
        status(result) shouldBe Status.OK
        contentAsString(result) should (
          include("Enter the UK bank account details you want us to pay your bonuses and transfer withdrawals into") and
          include("sortCode") and
          include("accountNumber") and
          include("accountName"))

      }
    }

    "handling submitBankDetails" must {

      val submitBankDetailsRequest = fakeRequestWithCSRFToken
        .withFormUrlEncodedBody(
          "sortCode" → "123456",
          "accountNumber" -> "12345678",
          "rollNumber" -> "",
          "accountName" -> "test user name"
        )

        def doRequest() = controller.submitBankDetails()(submitBankDetailsRequest)

      doCommonChecks(doRequest)

      "handle form errors during submit" in {
        val eligibilityResult = Some(Right(randomEligibleWithUserInfo(validUserInfo)))

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(Right(Some(HTSSession(eligibilityResult, None, None))))
        }

        val result = controller.submitBankDetails()(fakeRequestWithCSRFToken.withFormUrlEncodedBody("rollNumber" → "a"))
        status(result) shouldBe Status.OK
        contentAsString(result) should include("Enter sort code")
        contentAsString(result) should include("Enter account number")
        contentAsString(result) should include("Enter the name on the account")
        contentAsString(result) should include("Roll number must be 4 characters or more")
      }

      "handle cases when the backend bank details check mark the details as valid" in {
        val eligibilityResult = Some(Right(randomEligibleWithUserInfo(validUserInfo)))

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(Right(Some(HTSSession(eligibilityResult, None, None))))
          mockValidateBankDetails(ValidateBankDetailsRequest(nino, "123456", "12345678"))(Right(ValidateBankDetailsResult(false, true)))
        }

        val result = doRequest()
        status(result) shouldBe 200
        contentAsString(result) should include("Check your sort code is correct")
        contentAsString(result) should include("Check your account number is correct")
        contentAsString(result) should include("Enter the UK bank account details you want")
      }

      "handle cases when the backend bank details check mark the sort code as non-existent" in {
        val eligibilityResult = Some(Right(randomEligibleWithUserInfo(validUserInfo)))

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(Right(Some(HTSSession(eligibilityResult, None, None))))
          mockValidateBankDetails(ValidateBankDetailsRequest(nino, "123456", "12345678"))(Right(ValidateBankDetailsResult(true, false)))
        }

        val result = doRequest()
        status(result) shouldBe 200
        contentAsString(result) should include("Check your sort code is correct")
        contentAsString(result) should not include ("Check your account number is correct")
        contentAsString(result) should include("Enter the UK bank account details you want")
      }

      "update the session with bank details when all other checks are passed" in {

        val eligibilityResult = Some(Right(randomEligibleWithUserInfo(validUserInfo)))

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(Right(Some(HTSSession(eligibilityResult, None, None))))
          mockValidateBankDetails(ValidateBankDetailsRequest(nino, "123456", "12345678"))(Right(ValidateBankDetailsResult(true, true)))
          mockSessionStorePut(HTSSession(eligibilityResult, None, None, None, None, Some(BankDetails(SortCode(1, 2, 3, 4, 5, 6), "12345678", None, "test user name"))))(Right(()))
        }

        val result = doRequest()
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.RegisterController.getCreateAccountPage().url)
      }

      "handle mongo session errors during storing bank details in session" in {

        val eligibilityResult = Some(Right(randomEligibleWithUserInfo(validUserInfo)))

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(Right(Some(HTSSession(eligibilityResult, None, None))))
          mockValidateBankDetails(ValidateBankDetailsRequest(nino, "123456", "12345678"))(Right(ValidateBankDetailsResult(true, true)))
          mockSessionStorePut(HTSSession(eligibilityResult, None, None, None, None, Some(BankDetails(SortCode(1, 2, 3, 4, 5, 6), "12345678", None, "test user name"))))(Left("error"))
        }

        val result = doRequest()
        checkIsTechnicalErrorPage(result)
      }

      "handle the case when the request comes from already enrolled user" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
        }

        val result = doRequest()
        status(result) shouldBe 303
      }
    }
  }

  private def doCommonChecks(doRequest: () ⇒ Future[Result]): Unit = {
    "redirect user to eligibility checks if there is no session found" in {
      inSequence {
        mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
        mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        mockSessionStoreGet(Right(None))
      }

      val result = doRequest()
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility().url)
    }

    "redirect user to eligibility checks if there is a session but no eligibility result found in the session" in {
      inSequence {
        mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
        mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        mockSessionStoreGet(Right(Some(HTSSession(None, None, None))))
      }

      val result = doRequest()
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility().url)
    }

    "show user an in-eligible page if the session is found but user is not eligible" in {

      inSequence {
        mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
        mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        mockSessionStoreGet(Right(Some(HTSSession(Some(Left(randomIneligibility())), None, None))))
      }

      val result = doRequest()
      status(result) shouldBe 200
      contentAsString(result) should include("You’re not eligible for a Help to Save account")
    }

    "show user an error page if the session is found but user is not eligible and in-eligibility reason can't be parsed" in {
      val eligibilityCheckResult = randomIneligibility().value.eligibilityCheckResult.copy(reasonCode = 999)
      inSequence {
        mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
        mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        mockSessionStoreGet(Right(Some(HTSSession(Some(Left(randomIneligibility().copy(value =
          EligibilityCheckResponse(eligibilityCheckResult, randomEligibility().value.threshold)))), None, None))))
      }

      val result = doRequest()
      checkIsTechnicalErrorPage(result)
    }
  }

}
