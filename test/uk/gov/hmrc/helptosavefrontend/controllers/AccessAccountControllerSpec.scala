/*
 * Copyright 2017 HM Revenue & Customs
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

import play.api.i18n.MessagesApi
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavefrontend.models.EnrolmentStatus
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithCL200

class AccessAccountControllerSpec extends AuthSupport with EnrolmentAndEligibilityCheckBehaviour {

  lazy val controller = new AccessAccountController(
    fakeApplication.injector.instanceOf[MessagesApi],
    mockHelpToSaveService,
    mockAuthConnector,
    mockMetrics
  )

  "The AccessAccountController" must {

      def doRequest(): Result = await(controller.accessAccount(FakeRequest()))

    "redirect to NS&I if the user is enrolled" in {
      inSequence {
        mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
        mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
      }

      val result = doRequest()
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.NSIController.goToNSI().url)
    }

    "redirect to NS&I if the user is enrolled and set the ITMP flag if " +
      "it hasn't already been set" in {
        inSequence {
          mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(false)))
          mockWriteITMPFlag(Right(()))
        }

        val result = doRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.NSIController.goToNSI().url)
      }

    "show the user the 'do you want to check eligibility' page if the user is not enrolled" in {
      inSequence {
        mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
        mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
      }

      val result = doRequest()
      status(result) shouldBe 200
      contentAsString(result) should include("Click here to check your eligibility for Help To Save")
    }

    "proceed to do the eligibility checks if there is an error doing the enrolment check" in {
      inSequence {
        mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
        mockEnrolmentCheck()(Left(""))
      }

      val result = doRequest()
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility().url)
    }
  }

}
