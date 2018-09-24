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

import org.scalamock.handlers.{CallHandler3, CallHandler5}
import play.api.http.Status
import play.api.mvc.Result
import play.api.test.Helpers.{contentAsString, _}
import uk.gov.hmrc.helptosavefrontend.forms.{BankDetails, BankDetailsValidation, SortCode}
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithCL200
import uk.gov.hmrc.helptosavefrontend.models.TestData.Eligibility.{randomEligibleWithUserInfo, randomIneligibility}
import uk.gov.hmrc.helptosavefrontend.models.TestData.UserData.validUserInfo
import uk.gov.hmrc.helptosavefrontend.models.{EnrolmentStatus, HTSSession}
import uk.gov.hmrc.helptosavefrontend.services.BarsService
import uk.gov.hmrc.helptosavefrontend.util.NINO
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class BankAccountControllerSpec extends AuthSupport
  with CSRFSupport
  with EnrolmentAndEligibilityCheckBehaviour
  with SessionCacheBehaviourSupport {

  implicit lazy val bankDetailsValidation: BankDetailsValidation = new BankDetailsValidation(appConfig)

  val mockBarsService = mock[BarsService]

  def mockValidateBankDetails(bankDetails: BankDetails, nino: String, path: String)(response: Either[String, Boolean]) =
    (mockBarsService.validate(_: NINO, _: BankDetails, _: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nino, bankDetails, path, *, *)
      .returning(Future.successful(response))

  val controller = new BankAccountController(
    mockHelpToSaveService,
    mockSessionCacheConnector,
    mockAuthConnector,
    mockMetrics,
    mockBarsService)

  "The BankAccountController" when {

    "handling getBankDetailsPage" must {

        def doRequest() = controller.getBankDetailsPage()(fakeRequestWithCSRFToken)

      doCommonChecks(doRequest)

      "handle the request and display the page" in {

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, None))))
        }

        val result = doRequest()
        status(result) shouldBe Status.OK
        contentAsString(result) should include("Which UK bank account do you want us to pay your bonuses and withdrawals into?")

      }

      "display the page with correct Back link when they came from SelectEmail page" in {

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, None))))
        }

        val result = doRequest()
        status(result) shouldBe Status.OK
        contentAsString(result) should include("Which UK bank account do you want us to pay your bonuses and withdrawals into?")
        contentAsString(result) should include("/help-to-save/select-email")

      }

      "display the page with correct Back link when they came from check details page" in {

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, None, None, None, None, true))))
        }

        val result = doRequest()
        status(result) shouldBe Status.OK
        contentAsString(result) should include("Which UK bank account do you want us to pay your bonuses and withdrawals into?")
        contentAsString(result) should include("/help-to-save/create-account")

      }

      "send already stored bank details in the session back to the UI" in {

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, None, None, None, Some(BankDetails(SortCode(1, 2, 3, 4, 5, 6), "accountNumber", Some(""), "accountName"))))))
        }

        val result = doRequest()
        status(result) shouldBe Status.OK
        contentAsString(result) should (
          include("Which UK bank account do you want us to pay your bonuses and withdrawals into?") and
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

      "handle form errors during submit" in {
        val eligibilityResult = Some(Right(randomEligibleWithUserInfo(validUserInfo)))

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(eligibilityResult, None, None))))
        }

        val result = controller.submitBankDetails()(fakeRequestWithCSRFToken.withFormUrlEncodedBody("rollNumber" → "a"))
        status(result) shouldBe Status.OK
        contentAsString(result) should include("Your sort code needs to be 6 numbers")
        contentAsString(result) should include("Your account number needs to be 8 numbers")
        contentAsString(result) should include("Your account name needs to be 2 characters or more")
        contentAsString(result) should include("Your roll number needs to be between 4 and 18 characters")
      }

      doCommonChecks(doRequest)

      "update the session with bank details when all other checks are passed" in {

        val eligibilityResult = Some(Right(randomEligibleWithUserInfo(validUserInfo)))

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(eligibilityResult, None, None))))
          mockValidateBankDetails(BankDetails(SortCode(1, 2, 3, 4, 5, 6), "12345678", None, "test user name"), validUserInfo.nino, routes.BankAccountController.submitBankDetails().url)(Right(true))
          mockSessionCacheConnectorPut(HTSSession(eligibilityResult, None, None, None, None, Some(BankDetails(SortCode(1, 2, 3, 4, 5, 6), "12345678", None, "test user name"))))(Right(()))
        }

        val result = doRequest()
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.RegisterController.getCreateAccountPage().url)
      }

      "handle keystore errors during storing bank details in session" in {

        val eligibilityResult = Some(Right(randomEligibleWithUserInfo(validUserInfo)))

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(eligibilityResult, None, None))))
          mockValidateBankDetails(BankDetails(SortCode(1, 2, 3, 4, 5, 6), "12345678", None, "test user name"), validUserInfo.nino, routes.BankAccountController.submitBankDetails().url)(Right(true))
          mockSessionCacheConnectorPut(HTSSession(eligibilityResult, None, None, None, None, Some(BankDetails(SortCode(1, 2, 3, 4, 5, 6), "12345678", None, "test user name"))))(Left(("error")))
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
        mockSessionCacheConnectorGet(Right(None))
      }

      val result = doRequest()
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility().url)
    }

    "redirect user to eligibility checks if there is a session but no eligibility result found in the session" in {
      inSequence {
        mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
        mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None, None))))
      }

      val result = doRequest()
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility().url)
    }

    "show user an in-eligible page if the session is found but user is not eligible" in {

      inSequence {
        mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
        mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Left(randomIneligibility())), None, None))))
      }

      val result = doRequest()
      status(result) shouldBe 200
      contentAsString(result) should include("You’re not eligible for a Help to Save account")
    }

    "show user an error page if the session is found but user is not eligible and in-eligibility reason can't be parsed" in {

      inSequence {
        mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
        mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Left(randomIneligibility().copy(value = randomIneligibility().value.copy(reasonCode = 999)))), None, None))))
      }

      val result = doRequest()
      checkIsTechnicalErrorPage(result)
    }
  }

}
