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

import play.api.http.Status
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavefrontend.models.EnrolmentStatus
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithCL200
import uk.gov.hmrc.helptosavefrontend.views.html.email.accountholder.check_your_details

class CheckYourDetailsControllerSpec
    extends ControllerSpecWithGuiceApp with AuthSupport with EnrolmentAndEligibilityCheckBehaviour
    with SessionStoreBehaviourSupport {

  private val controller = new CheckYourDetailsController(
    mockHelpToSaveService,
    mockAuthConnector,
    mockMetrics,
    testCpd,
    testMcc,
    testErrorHandler,
    testMaintenanceSchedule,
    injector.instanceOf[check_your_details]
  )

  private val fakeRequest = FakeRequest("GET", "/")

  "The CheckYourDetailsController" when {
    "handling checkYourDetails" must {
      def doRequest() = controller.checkYourDetails()(fakeRequest)

      "handle the request and display the check your details page" in {
        mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
        mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))

        val result = doRequest()
        status(result) shouldBe Status.OK
        contentAsString(result).trim should include(
          "Your details"
        )
      }

      "handle the request and redirect to the missing details page when user details are missing" in {
        mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsMissingUserInfo)
        mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))

        val result = doRequest()
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some("/help-to-save/missing-details")
      }

      "handle the request and redirect to the missing details page when user postcode is missing" in {
        mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsMissingPostcode)
        mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))

        val result = doRequest()
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some("/help-to-save/missing-details")
      }
    }
  }
}
