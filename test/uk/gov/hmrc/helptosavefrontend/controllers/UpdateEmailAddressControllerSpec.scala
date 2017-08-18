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

import play.api.http.Status
import play.api.i18n.MessagesApi
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.config.FrontendAuthConnector
import uk.gov.hmrc.helptosavefrontend.repo.EnrolmentStore
import uk.gov.hmrc.helptosavefrontend.models.{HTSSession, validNSIUserInfo}
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithConfidence

import scala.concurrent.Future

class UpdateEmailAddressControllerSpec extends TestSupport with EnrolmentAndEligibilityCheckBehaviour {

  val frontendAuthConnector = stub[FrontendAuthConnector]

  val controller = new UpdateEmailAddressController(mockSessionCacheConnector, mockEnrolmentService, frontendAuthConnector
  )(fakeApplication, fakeApplication.injector.instanceOf[MessagesApi]){
    override val authConnector = mockAuthConnector
  }

  "The UpdateEmailAddressController" when {

    "getting the update your email page " must {

      def getResult(): Future[Result] = controller.getUpdateYourEmailAddress(FakeRequest())

      commonEnrolmentAndSessionBehaviour(getResult)

      "return the update your email page if the user is not already enrolled and the " +
        "session data indicates that they are eligible" in {
        inSequence {
          mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
          mockEnrolmentCheck(nino)(Right(EnrolmentStore.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), None))))
        }

        val result = getResult()
        status(result) shouldBe Status.OK
        contentAsString(result) should include("Update your email address")
      }


      "return the you're not aeligible page if the user is not already enrolled and the " +
        "session data indicates that they are ineligible" in {
        inSequence {
          mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
          mockEnrolmentCheck(nino)(Right(EnrolmentStore.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None))))
        }

        val result = getResult()
        status(result) shouldBe Status.OK
        contentAsString(result) should include("not eligible")
      }

    }

  }

}
