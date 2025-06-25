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
import play.api.http.Status
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavefrontend.forms.BankDetailsValidation
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithCL200
import uk.gov.hmrc.helptosavefrontend.models.TestData.Eligibility.{randomEligibility, randomEligibleWithUserInfo, randomIneligibility}
import uk.gov.hmrc.helptosavefrontend.models.TestData.UserData.validUserInfo
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityCheckResponse
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.views.html.register.bank_account_details

import scala.concurrent.Future

class BankAccountControllerSpec
    extends ControllerSpecWithGuiceApp with CSRFSupport with AuthSupport with EnrolmentAndEligibilityCheckBehaviour
    with SessionStoreBehaviourSupport {

  implicit lazy val bankDetailsValidation: BankDetailsValidation = new BankDetailsValidation(appConfig)

  def mockValidateBankDetails(
    request: ValidateBankDetailsRequest
  )(response: Either[String, ValidateBankDetailsResult]) =
    when(mockHelpToSaveService.validateBankDetails(eqTo(request))(any(), any()))
      .thenReturn(EitherT.fromEither[Future](response))

  val controller = new BankAccountController(
    mockHelpToSaveService,
    mockSessionStore,
    mockAuthConnector,
    mockMetrics,
    testCpd,
    testMcc,
    testErrorHandler,
    testMaintenanceSchedule,
    injector.instanceOf[bank_account_details]
  )

  private val fakeRequest = FakeRequest("GET", "/")

  "The BankAccountController" when {

    "handling getBankDetailsPage" must {

      def doRequest() = csrfAddToken(controller.getBankDetailsPage())(fakeRequest)

      doCommonChecks(() => doRequest())

      "handle the request and display the page" in {

        mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
        mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        mockSessionStoreGet(
          Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, None)))
        )

        val result = doRequest()
        status(result) shouldBe Status.OK
        contentAsString(result) should include(
          "Enter the UK bank account details you want us to pay your bonuses and transfer withdrawals into"
        )

      }

      "display the page with correct Back link when they came from applySavingsReminders page" in {

        mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
        mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        mockSessionStoreGet(
          Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, None)))
        )

        val result = doRequest()
        status(result) shouldBe Status.OK
        contentAsString(result) should include(
          "Enter the UK bank account details you want us to pay your bonuses and transfer withdrawals into"
        )
        contentAsString(result) should include("/help-to-save/apply-savings-reminders")
      }

      "display the page with correct Back link when they came from emailVerified page" in {

        mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
        mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        mockSessionStoreGet(
          Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, Some("pendingEmail"))))
        )

        val result = doRequest()
        status(result) shouldBe Status.OK
        contentAsString(result) should include(
          "Enter the UK bank account details you want us to pay your bonuses and transfer withdrawals into"
        )
        contentAsString(result) should include("/help-to-save/email-confirmed")
      }

      "display the page with correct Back link when they came from check details page" in {

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

        val result = doRequest()
        status(result) shouldBe Status.OK
        contentAsString(result) should include(
          "Enter the UK bank account details you want us to pay your bonuses and transfer withdrawals into"
        )
        contentAsString(result) should include("/help-to-save/create-account")

      }

      "send already stored bank details in the session back to the UI" in {

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
                Some(BankDetails("accountName", SortCode(1, 2, 3, 4, 5, 6), "accountNumber", Some("")))
              )
            )
          )
        )

        val result = doRequest()
        status(result) shouldBe Status.OK
        contentAsString(result) should (include(
          "Enter the UK bank account details you want us to pay your bonuses and transfer withdrawals into"
        ) and
          include("sortCode") and
          include("accountNumber") and
          include("accountName") and
          include("rollNumber") and
          include("Building society roll number (if you have one)"))
      }
    }

    "handling submitBankDetails" must {

      val submitBankDetailsRequest = fakeRequest
        .withMethod("POST")
        .withFormUrlEncodedBody(
          "sortCode"      -> "123456",
          "accountNumber" -> "12345678",
          "rollNumber"    -> "",
          "accountName"   -> "test user name"
        )

      def doRequest() = csrfAddToken(controller.submitBankDetails())(submitBankDetailsRequest)

      doCommonChecks(() => doRequest())

      "handle form errors during submit" in {
        val eligibilityResult = Some(Right(randomEligibleWithUserInfo(validUserInfo)))

        mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
        mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        mockSessionStoreGet(Right(Some(HTSSession(eligibilityResult, None, None))))

        val result =
          csrfAddToken(controller.submitBankDetails())(
            fakeRequest.withMethod("POST").withFormUrlEncodedBody("rollNumber" -> "a")
          )
        status(result) shouldBe Status.OK
        contentAsString(result) should include("Enter a sort code")
        contentAsString(result) should include("Enter an account number")
        contentAsString(result) should include("Enter the name on the account")
        contentAsString(result) should include("Roll number must be 4 characters or more")
      }

      "handle cases when the backend bank details check mark the details as valid" in {
        val eligibilityResult = Some(Right(randomEligibleWithUserInfo(validUserInfo)))

        mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
        mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        mockSessionStoreGet(Right(Some(HTSSession(eligibilityResult, None, None))))
        mockValidateBankDetails(ValidateBankDetailsRequest(nino, "123456", "12345678"))(
          Right(ValidateBankDetailsResult(false, true))
        )

        val result = doRequest()
        status(result) shouldBe 200
        contentAsString(result) should include("Check your sort code is correct")
        contentAsString(result) should include("Check your account number is correct")
        contentAsString(result) should include("Enter the UK bank account details you want")
      }

      "handle cases when the backend bank details check mark the sort code as non-existent" in {
        val eligibilityResult = Some(Right(randomEligibleWithUserInfo(validUserInfo)))

        mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
        mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        mockSessionStoreGet(Right(Some(HTSSession(eligibilityResult, None, None))))
        mockValidateBankDetails(ValidateBankDetailsRequest(nino, "123456", "12345678"))(
          Right(ValidateBankDetailsResult(true, false))
        )

        val result = doRequest()
        status(result) shouldBe 200
        contentAsString(result) should include("Check your sort code is correct")
        contentAsString(result) should not include ("Check your account number is correct")
        contentAsString(result) should include("Enter the UK bank account details you want")
      }

      "update the session with bank details when all other checks are passed" in {

        val eligibilityResult = Some(Right(randomEligibleWithUserInfo(validUserInfo)))

        mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
        mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        mockSessionStoreGet(Right(Some(HTSSession(eligibilityResult, None, None))))
        mockValidateBankDetails(ValidateBankDetailsRequest(nino, "123456", "12345678"))(
          Right(ValidateBankDetailsResult(true, true))
        )
        mockSessionStorePut(
          HTSSession(
            eligibilityResult,
            None,
            None,
            None,
            None,
            Some(BankDetails("test user name", SortCode(1, 2, 3, 4, 5, 6), "12345678", None))
          )
        )(Right(()))

        val result = doRequest()
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.RegisterController.getCreateAccountPage.url)
      }

      "handle mongo session errors during storing bank details in session" in {

        val eligibilityResult = Some(Right(randomEligibleWithUserInfo(validUserInfo)))

        mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
        mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        mockSessionStoreGet(Right(Some(HTSSession(eligibilityResult, None, None))))
        mockValidateBankDetails(ValidateBankDetailsRequest(nino, "123456", "12345678"))(
          Right(ValidateBankDetailsResult(true, true))
        )
        mockSessionStorePut(
          HTSSession(
            eligibilityResult,
            None,
            None,
            None,
            None,
            Some(BankDetails("test user name", SortCode(1, 2, 3, 4, 5, 6), "12345678", None))
          )
        )(Left("error"))

        val result = doRequest()
        checkIsTechnicalErrorPage(result)
      }

      "handle the case when the request comes from already enrolled user" in {
        mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
        mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))

        val result = doRequest()
        status(result) shouldBe 303
      }
    }
  }

  private def doCommonChecks(doRequest: () => Future[Result]): Unit = {
    "redirect user to eligibility checks if there is no session found" in {
      mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
      mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
      mockSessionStoreGet(Right(None))

      val result = doRequest()
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility.url)
    }

    "redirect user to eligibility checks if there is a session but no eligibility result found in the session" in {
      mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
      mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
      mockSessionStoreGet(Right(Some(HTSSession(None, None, None))))

      val result = doRequest()
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility.url)
    }

    "show user an in-eligible page if the session is found but user is not eligible" in {

      mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrievalWithPTEnrolment)
      mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
      mockSessionStoreGet(Right(Some(HTSSession(Some(Left(randomIneligibility())), None, None))))

      val result = doRequest()
      status(result) shouldBe SEE_OTHER
    }

    "show user an error page if the session is found but user is not eligible and in-eligibility reason can't be parsed" in {
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

      val result = doRequest()
      checkIsTechnicalErrorPage(result)
    }
  }

}
