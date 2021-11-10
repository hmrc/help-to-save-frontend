/*
 * Copyright 2021 HM Revenue & Customs
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
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithCL200
import uk.gov.hmrc.helptosavefrontend.models.{EnrolmentStatus, HTSSession}
import uk.gov.hmrc.helptosavefrontend.views.html.core.{confirm_check_eligibility, error_template}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class AccessAccountControllerSpec
    extends ControllerSpecWithGuiceApp with EnrolmentAndEligibilityCheckBehaviour with SessionStoreBehaviourSupport
    with CSRFSupport with AuthSupport {

  lazy val controller = new AccessAccountController(
    mockHelpToSaveService,
    mockAuthConnector,
    mockMetrics,
    mockSessionStore,
    testCpd,
    testMcc,
    testErrorHandler,
    testMaintenanceSchedule,
    injector.instanceOf[confirm_check_eligibility],
    injector.instanceOf[error_template]
  )

  "The AccessAccountController" when {

    "handling getSignInPage" must {

      "redirect to the govuk sign in page" in {
        (mockAuthConnector
          .authorise(_: Predicate, _: EmptyRetrieval.type)(_: HeaderCarrier, _: ExecutionContext))
          .expects(EmptyPredicate, EmptyRetrieval, *, *)
          .returning(Future.successful(Unit))

        val result = controller.getSignInPage()(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("https://www.gov.uk/sign-in-help-to-save")

      }
    }

    "handling accessAccount" must {

      def doRequest(): Future[Result] =
        Future.successful(await(controller.accessAccount(FakeRequest())))

      behave like commonBehaviour(doRequest, appConfig.nsiManageAccountUrl)

    }

    "handling payIn" must {

      def doRequest(): Future[Result] =
        Future.successful(await(controller.payIn(FakeRequest())))

      behave like commonBehaviour(doRequest, appConfig.nsiPayInUrl)

    }

    "handling getNoAccountPage" must {

      def doRequest() =
        Future.successful(await(controller.getNoAccountPage(FakeRequest())))

      "show the 'no account' page if the user is not enrolled" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionStoreGet(Right(None))
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        }

        val result = csrfAddToken(controller.getNoAccountPage)(FakeRequest())
        status(result) shouldBe 200
        contentAsString(result) should include("You do not have a Help to Save account")
      }

      "redirect to check eligibility if there is an error checking eligibility" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionStoreGet(Right(None))
          mockEnrolmentCheck()(Left(""))
        }

        val result = doRequest()
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility().url)
      }

      "redirect to a previously attempted redirect URL if the user is enrolled and the session indicates they were" +
        "trying to previously reach an account holder page" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionStoreGet(Right(Some(HTSSession.empty.copy(attemptedAccountHolderPageURL = Some("abc")))))
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
        }

        val result = doRequest()
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some("abc")
      }

      "redirect to the NS&I homepage by default if the user is enrolled and there is no session data" in {
        def test(session: Option[HTSSession]) =
          withClue(s"For session $session: ") {
            inSequence {
              mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
              mockSessionStoreGet(Right(session))
              mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
            }

            val result = doRequest()
            status(result) shouldBe 303
            redirectLocation(result) shouldBe Some(appConfig.nsiManageAccountUrl)
          }

        test(None)
        test(Some(HTSSession.empty))
      }

      "show an error page when there session data cannot be obtained" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockSessionStoreGet(Left(""))
        }

        val result = doRequest()
        checkIsTechnicalErrorPage(result)
      }

    }

    def commonBehaviour(doRequest: () ⇒ Future[Result], expectedRedirectURL: String): Unit = { // scalastyle:ignore
      "redirect to NS&I if the user is enrolled" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
        }

        val result = doRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(expectedRedirectURL)
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
        redirectLocation(result) shouldBe Some(expectedRedirectURL)
      }

      "redirect to the no-account page if the user is not enrolled to HTS" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStorePut(HTSSession.empty.copy(attemptedAccountHolderPageURL = Some(expectedRedirectURL)))(
            Right(())
          )
        }

        val result = doRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.AccessAccountController.getNoAccountPage().url)
      }

      "store the attempted redirect location and redirect to check eligibility if there is  an error" +
        "checking the enrolment status" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Left("Oh no!"))
          mockSessionStorePut(HTSSession.empty.copy(attemptedAccountHolderPageURL = Some(expectedRedirectURL)))(
            Right(())
          )
        }

        val result = doRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility().url)
      }

      "show an error screen if there is an error storing the session data" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Left("Oh no!"))
          mockSessionStorePut(HTSSession.empty.copy(attemptedAccountHolderPageURL = Some(expectedRedirectURL)))(
            Left("")
          )
        }

        val result = doRequest()
        checkIsTechnicalErrorPage(result)
      }

    }

  }
}
