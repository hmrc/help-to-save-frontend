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

import play.api.Configuration
import play.api.http.Status
import play.api.i18n.MessagesApi
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.config.{FrontendAuthConnector, WSHttp}
import uk.gov.hmrc.helptosavefrontend.connectors.{EmailVerificationConnector, EmailVerificationConnectorImpl}
import uk.gov.hmrc.helptosavefrontend.repo.EnrolmentStore
import uk.gov.hmrc.helptosavefrontend.models.{HTSSession, validNSIUserInfo}
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithConfidence
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class UpdateEmailAddressControllerSpec extends TestSupport with EnrolmentAndEligibilityCheckBehaviour {

  lazy val injector = fakeApplication.injector
  lazy val request = FakeRequest()
  def messagesApi = injector.instanceOf[MessagesApi]
  lazy val messages = messagesApi.preferred(request)

  val frontendAuthConnector = stub[FrontendAuthConnector]

  val mockEmailVerificationConnector = mock[EmailVerificationConnector]

  val mockHttp = mock[WSHttp]

//  lazy val emailVerificationConnector = {
//    val config = Configuration("services.email-verification.linkTTLMinutes" → " 120")
//    new EmailVerificationConnectorImpl(mockHttp, config)
//  }

  lazy val controller = new UpdateEmailAddressController(mockSessionCacheConnector, mockEnrolmentService, frontendAuthConnector, mockEmailVerificationConnector
  )(fakeApplication, fakeApplication.injector.instanceOf[MessagesApi]){
    override val authConnector = mockAuthConnector
  }

  def mockEmailVerificationConn = {
    (mockEmailVerificationConnector.verifyEmail(_: String, _:String)(_: HeaderCarrier)).expects(*,*,*)
      .returning(Future.successful(Right()))
  }

  "The UpdateEmailAddressController" when {

    "getting the update your email page " must {

      def getResult(): Future[Result] = controller.getUpdateYourEmailAddress(FakeRequest())

      testCommonEnrolmentAndSessionBehaviour(getResult)

      "return the update your email page if the user is not already enrolled and the " +
        "session data indicates that they are eligible" in {
        inSequence {
          mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
          mockEnrolmentCheck(nino)(Right(EnrolmentStore.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), None))))
        }

        val result = getResult()
        status(result) shouldBe Status.OK
        contentAsString(result) should include(messages("hts.email-verification.title"))
      }


      "return the you're not eligible page if the user is not already enrolled and the " +
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

  "onSubmit" should {
    "given a valid email address return the check your email page with a status of Ok" in {
      val fakePostRequest = FakeRequest().withFormUrlEncodedBody("value" → "email@gmail.com")
      inSequence {
        mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
        mockEmailVerificationConn
      }
      val result = controller.onSubmit()(fakePostRequest)
      status(result) shouldBe Status.OK
      contentAsString(result).contains(messagesApi("hts.email-verification.check-your-email.title"))
    }
  }

}
