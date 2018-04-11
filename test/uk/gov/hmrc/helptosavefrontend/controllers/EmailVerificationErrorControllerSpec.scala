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

import cats.data.EitherT
import cats.instances.future._
import play.api.http.Status
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.models.EnrolmentStatus
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithCL200
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class EmailVerificationErrorControllerSpec extends TestSupport with AuthSupport {

  val mockHelpToSaveService = mock[HelpToSaveService]

  lazy val controller = new EmailVerificationErrorController(
    mockHelpToSaveService,
    mockAuthConnector,
    mockMetrics
  ) {
    override val authConnector = mockAuthConnector
  }

  def mockEnrolmentCheck()(result: Either[String, EnrolmentStatus]): Unit =
    (mockHelpToSaveService.getUserEnrolmentStatus()(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *)
      .returning(EitherT.fromEither[Future](result))

  "The EmailVerificationErrorController" when {

    "handling verifyEmailErrorTryLater" must {

      "show the we couldn't update your email page if the user is not enrolled yet" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        }

        val result = controller.verifyEmailErrorTryLater(FakeRequest())
        status(result) shouldBe Status.OK
        contentAsString(result) should include("Something went wrong")
        contentAsString(result) should include("Go to About Help to Save")
      }

      "show the we couldn't update your email page if the user is enrolled" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
        }

        val result = controller.verifyEmailErrorTryLater(FakeRequest())
        status(result) shouldBe Status.OK
        contentAsString(result) should include("We cannot change your email address at this time")
        contentAsString(result) should include("Go to account home")
      }

      "return an error if there is an error checking the users eligibility" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Left(""))
        }

        val result = controller.verifyEmailErrorTryLater(FakeRequest())
        checkIsTechnicalErrorPage(result)
      }

    }

  }

}

