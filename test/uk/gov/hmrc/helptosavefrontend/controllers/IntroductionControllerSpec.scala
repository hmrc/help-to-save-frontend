/*
 * Copyright 2020 HM Revenue & Customs
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
import uk.gov.hmrc.auth.core.authorise.{EmptyPredicate, Predicate}
import uk.gov.hmrc.auth.core.retrieve.EmptyRetrieval
import uk.gov.hmrc.helptosavefrontend.models.EnrolmentStatus.{Enrolled, NotEnrolled}
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithCL200
import uk.gov.hmrc.helptosavefrontend.models.account.AccountNumber
import uk.gov.hmrc.helptosavefrontend.views.html.core.privacy
import uk.gov.hmrc.helptosavefrontend.views.html.helpinformation.help_information
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class IntroductionControllerSpec
    extends ControllerSpecWithGuiceApp with AuthSupport with CSRFSupport with SessionStoreBehaviourSupport
    with EnrolmentAndEligibilityCheckBehaviour {

  val fakeRequest = FakeRequest("GET", "/")
  val helpToSave = new IntroductionController(
    mockAuthConnector,
    mockMetrics,
    mockHelpToSaveService,
    testCpd,
    testMcc,
    testErrorHandler,
    testMaintenanceSchedule,
    injector.instanceOf[privacy],
    injector.instanceOf[help_information]
  )

  def mockAuthorise(loggedIn: Boolean) =
    (mockAuthConnector
      .authorise(_: Predicate, _: EmptyRetrieval.type)(_: HeaderCarrier, _: ExecutionContext))
      .expects(EmptyPredicate, EmptyRetrieval, *, *)
      .returning(if (loggedIn) Future.successful(()) else Future.failed(new Exception("")))

  "the about help to save page" should {
    "redirect to correct GOV.UK page" in {
      mockAuthorise(false)

      val result = helpToSave.getAboutHelpToSave(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some("https://www.gov.uk/get-help-savings-low-income")
    }

    "the getEligibility should redirect to correct GOV.UK page" in {
      mockAuthorise(true)

      val result = helpToSave.getEligibility(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some("https://www.gov.uk/get-help-savings-low-income/eligibility")
    }

    "the getHowTheAccountWorks return 200" in {
      mockAuthorise(false)

      val result = helpToSave.getHowTheAccountWorks(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some("https://www.gov.uk/get-help-savings-low-income")
    }

    "the getHowWeCalculateBonuses return 200" in {
      mockAuthorise(true)

      val result = helpToSave.getHowWeCalculateBonuses(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some("https://www.gov.uk/get-help-savings-low-income/what-youll-get")
    }

    "the getApply return 200" in {
      mockAuthorise(false)

      val result = csrfAddToken(helpToSave.getApply)(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some("https://www.gov.uk/get-help-savings-low-income/how-to-apply")
    }

    "getHelpPage" should {

      "show the help page content if the user is logged in and has a HTS account" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(Some(nino))
          mockEnrolmentCheck()(Right(Enrolled(true)))
          mockGetAccountNumberFromService()(Right(AccountNumber(Some(accountNumber))))
        }

        val result = helpToSave.getHelpPage(FakeRequest())
        status(result) shouldBe OK
        val content = contentAsString(result)
        content should include("Help and information")
        content should include("A calendar month is a full month from the first day of the month")
      }

      "show an error page if the user's enrolment status cannot be checked" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(Some(nino))
          mockEnrolmentCheck()(Left(""))
        }

        val result = helpToSave.getHelpPage(FakeRequest())
        checkIsTechnicalErrorPage(result)
      }

      "show the no-account page if the user does not have a HTS account" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(Some(nino))
          mockEnrolmentCheck()(Right(NotEnrolled))
        }

        val result = helpToSave.getHelpPage(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.AccessAccountController.getNoAccountPage().url)
      }

    }

  }
}
