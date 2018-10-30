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
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.authorise.{EmptyPredicate, Predicate}
import uk.gov.hmrc.auth.core.retrieve.EmptyRetrieval
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class IntroductionControllerSpec extends AuthSupport with CSRFSupport {

  val fakeRequest = FakeRequest("GET", "/")
  val helpToSave = new IntroductionController(mockAuthConnector, mockMetrics)

  def mockAuthorise(loggedIn: Boolean) =
    (mockAuthConnector.authorise(_: Predicate, _: EmptyRetrieval.type)(_: HeaderCarrier, _: ExecutionContext))
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

      val result = helpToSave.getApply(fakeRequestWithCSRFToken)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some("https://www.gov.uk/get-help-savings-low-income/how-to-apply")
    }
  }
}
