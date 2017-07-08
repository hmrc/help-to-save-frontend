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

package uk.gov.hmrc.helptosavefrontend.services

import cats.data.EitherT
import uk.gov.hmrc.helptosavefrontend.connectors.NSIConnector.{SubmissionFailure, SubmissionSuccess}
import uk.gov.hmrc.helptosavefrontend.connectors.{HelpToSaveConnector, NSIConnector}
import uk.gov.hmrc.helptosavefrontend.models.{EligibilityCheckResult, NSIUserInfo}
import uk.gov.hmrc.helptosavefrontend.util.Result
import uk.gov.hmrc.helptosavefrontend.{TestSupport, models}
import uk.gov.hmrc.play.http.HeaderCarrier
import cats.instances.future._

import scala.concurrent.{ExecutionContext, Future}

class HelpToSaveServiceSpec extends TestSupport {

  val htsConnector = mock[HelpToSaveConnector]
  val nsiConnector = mock[NSIConnector]

  val htsService = new HelpToSaveService(htsConnector, nsiConnector)

  "The HelpToSaveService" when {

    "checking eligibility" must {

      val nino = "WM123456C"
      val oauthAuthorisationCode = "authorisation"

      "return a successful response" in {

        val user = models.randomUserInfo()

        val eligResult = EligibilityCheckResult(Right(Some(user)))

        val eligStatus: Result[EligibilityCheckResult] = EitherT.pure[Future, String, EligibilityCheckResult](eligResult)

        (htsConnector.getEligibility(_: String,  _: String)(_: HeaderCarrier)).expects(nino, oauthAuthorisationCode, *)
          .returning(eligStatus)

        val result = htsService.checkEligibility(nino, oauthAuthorisationCode)

        result.value.futureValue should be(Right(eligResult))
      }
    }

    "createAccount" must {

      val nsiUserInfo = models.validNSIUserInfo

      "return a successful response" in {

        (nsiConnector.createAccount(_: NSIUserInfo)(_: HeaderCarrier, _: ExecutionContext)).expects(nsiUserInfo, *, *)
          .returning(Future.successful(SubmissionSuccess()))

        val result = htsService.createAccount(nsiUserInfo)

        result.value.futureValue should be(Right(SubmissionSuccess()))

      }

      "should handle a failure result" in {

        val failureResponse = SubmissionFailure(Some("submission failure"), "failure message", "failure details")

        (nsiConnector.createAccount(_: NSIUserInfo)(_: HeaderCarrier, _: ExecutionContext)).expects(nsiUserInfo, *, *)
          .returning(Future.successful(failureResponse))

        val result = htsService.createAccount(nsiUserInfo)

        result.value.futureValue should be(Left(failureResponse))

      }
    }
  }
}
