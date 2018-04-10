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

import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._

import uk.gov.hmrc.auth.core.authorise.{EmptyPredicate, Predicate}
import uk.gov.hmrc.auth.core.retrieve.EmptyRetrieval
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.models.EnrolmentStatus
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithCL200
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class AccessAccountControllerSpec extends AuthSupport with EnrolmentAndEligibilityCheckBehaviour with SessionCacheBehaviour with CSRFSupport {

  lazy val controller = new AccessAccountController(
    mockHelpToSaveService,
    mockAuthConnector,
    mockMetrics)

  "The AccessAccountController" when {

    "handling getSignInPage" must {

      "return the sign in page" in {
        (mockAuthConnector.authorise(_: Predicate, _: EmptyRetrieval.type)(_: HeaderCarrier, _: ExecutionContext))
          .expects(EmptyPredicate, EmptyRetrieval, *, *)
          .returning(())

        val result = controller.getSignInPage()(FakeRequest())
        status(result) shouldBe OK
        contentAsString(result) should include("Sign in to your Help to Save Account")

      }
    }

    "handling accessAccount" must {

        def doRequest(): Result = await(controller.accessAccount(FakeRequest()))

      behave like commonBehaviour(doRequest)

      "redirect to the 'no account' page if the user is not enrolled" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        }

        val result = doRequest()
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.AccessAccountController.getNoAccountPage().url)
      }

      "proceed to do the eligibility checks if there is an error doing the enrolment check" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Left(""))
        }

        val result = doRequest()
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility().url)
      }
    }

    "handling getNoAccountPage" must {

        def doRequest(): Result = await(controller.getNoAccountPage(FakeRequest()))

      behave like commonBehaviour(doRequest)

      "show the 'no account' page if the user is not enrolled" in {

          def doRequestWithCSRFToken(): Result = await(controller.getNoAccountPage(fakeRequestWithCSRFToken))

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        }

        val result = doRequestWithCSRFToken()
        status(result) shouldBe 200
        contentAsString(result) should include("If you want to apply for an account, you should continue")
      }

      "redirect to accessAccount if there is an error checking eligibility" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Left(""))
        }

        val result = doRequest()
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.AccessAccountController.accessAccount().url)
      }

    }

      def commonBehaviour(doRequest: () ⇒ Future[Result]): Unit = {
        "redirect to NS&I if the user is enrolled" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
          }

          val result = doRequest()
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(appConfig.nsiManageAccountUrl)
        }

        "redirect to NS&I if the user is enrolled and set the ITMP flag if " +
          "it hasn't already been set" in {
            inSequence {
              mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
              mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(false)))
              mockWriteITMPFlag(Right(()))
            }

            val result = doRequest()
            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some(appConfig.nsiManageAccountUrl)
          }
      }

  }
}
