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

import play.api.http.Status
import play.api.mvc.Result
import play.api.test.Helpers.{contentAsString, _}
import uk.gov.hmrc.helptosavefrontend.forms.BankDetails
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithCL200
import uk.gov.hmrc.helptosavefrontend.models.TestData.Eligibility.{randomEligibleWithUserInfo, randomIneligibility}
import uk.gov.hmrc.helptosavefrontend.models.TestData.UserData.validUserInfo
import uk.gov.hmrc.helptosavefrontend.models.{EnrolmentStatus, HTSSession}

import scala.concurrent.Future

class BankAccountControllerSpec extends AuthSupport
  with CSRFSupport
  with EnrolmentAndEligibilityCheckBehaviour
  with SessionCacheBehaviourSupport {

  val controller = new BankAccountController(
    mockHelpToSaveService,
    mockSessionCacheConnector,
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
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, None))))
        }

        val result = doRequest()
        status(result) shouldBe Status.OK
        contentAsString(result) should include("Enter Bank Details")

      }
    }

    "handling submitBankDetails" must {

      val submitBankDetailsRequest = fakeRequestWithCSRFToken
        .withFormUrlEncodedBody(
          "sortCode" → "123456",
          "accountNumber" -> "12345678",
          "rollNumbber" -> "",
          "accountName" -> "test user name"
        )

        def doRequest() = controller.submitBankDetails()(submitBankDetailsRequest)

      "handle form errors during submit" in {

        mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)

        val result = controller.submitBankDetails()(fakeRequestWithCSRFToken)
        status(result) shouldBe Status.OK
        contentAsString(result) should include("sortCode This field is required")
        contentAsString(result) should include("accountNumber This field is required")
        contentAsString(result) should include("accountName This field is required")
      }

      doCommonChecks(doRequest)

      "update the session with bank details when all other checks are passed" in {

        val eligibilityResult = Some(Right(randomEligibleWithUserInfo(validUserInfo)))

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(eligibilityResult, None, None))))
          mockSessionCacheConnectorPut(HTSSession(eligibilityResult, None, None, None, None, Some(BankDetails("123456", "12345678", None, "test user name"))))(Right(()))
        }

        val result = doRequest()
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.RegisterController.checkDetails().url)
      }

      "handle keystore errors during storing bank details in session" in {

        val eligibilityResult = Some(Right(randomEligibleWithUserInfo(validUserInfo)))

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(eligibilityResult, None, None))))
          mockSessionCacheConnectorPut(HTSSession(eligibilityResult, None, None, None, None, Some(BankDetails("123456", "12345678", None, "test user name"))))(Left(("error")))
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
