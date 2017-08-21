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
import cats.instances.future._
import uk.gov.hmrc.helptosavefrontend.connectors.NSIConnector.{SubmissionFailure, SubmissionSuccess}
import uk.gov.hmrc.helptosavefrontend.connectors.{HelpToSaveConnector, NSIConnector}
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.{TestSupport, models}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class HelpToSaveServiceSpec extends TestSupport {

  val htsConnector = mock[HelpToSaveConnector]
  val nsiConnector = mock[NSIConnector]

  val htsService = new HelpToSaveServiceImpl(htsConnector, nsiConnector)

  "The HelpToSaveService" when {

    "checking eligibility" must {

      val nino = "WM123456C"

      "return a successful response if the connector returns a successful response" in {

        val eligibilityCheckResult = randomEligibilityCheckResult()

        (htsConnector.getEligibility(_: String)(_: HeaderCarrier)).expects(nino, *)
          .returning(EitherT.pure(eligibilityCheckResult))

        val result = htsService.checkEligibility(nino)
        result.value.futureValue should be(Right(eligibilityCheckResult))
      }

      "return an unsuccessful response if the connector returns an unsuccessful response" in {
        (htsConnector.getEligibility(_: String)(_: HeaderCarrier)).expects(nino, *)
          .returning(EitherT.fromEither[Future](Left("uh oh")))

        val result = htsService.checkEligibility(nino)
        result.value.futureValue should be(Left("uh oh"))
      }
    }

    "getting user information" must {

      val nino = "nino"
      val userDetailsURI = "uri"

      "return a successful response if the connector responds with a successful response" in {
        val userInfo = randomUserInfo()

        (htsConnector.getUserInformation(_: String, _: String)(_: HeaderCarrier))
          .expects(nino, userDetailsURI, *)
          .returning(EitherT.pure(userInfo))

        val result = htsService.getUserInformation(nino, userDetailsURI)
        result.value.futureValue should be(Right(userInfo))
      }


      "return an unsuccessful response if the connector responds with an unsuccessful response" in {
        val error = randomUserInformationRetrievalError()


        (htsConnector.getUserInformation(_: String, _: String)(_: HeaderCarrier))
          .expects(nino, userDetailsURI, *)
          .returning(EitherT.fromEither[Future](Left(error)))

        val result = htsService.getUserInformation(nino, userDetailsURI)
        result.value.futureValue should be(Left(error))
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
