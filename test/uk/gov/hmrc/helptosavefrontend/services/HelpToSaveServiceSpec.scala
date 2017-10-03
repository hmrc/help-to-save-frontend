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
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class HelpToSaveServiceSpec extends TestSupport {

  val htsConnector = mock[HelpToSaveConnector]
  val nsiConnector = mock[NSIConnector]

  val htsService = new HelpToSaveServiceImpl(htsConnector, nsiConnector)

  "The HelpToSaveService" when {

    "get user enrolment status" must {

      val nino = "WM123456C"

      "return a successful response" in {

        (htsConnector.getUserEnrolmentStatus()(_: HeaderCarrier)).expects(*)
          .returning(EitherT.pure(EnrolmentStatus.Enrolled(true)))

        val result = htsService.getUserEnrolmentStatus()
        result.value.futureValue should be(Right(EnrolmentStatus.Enrolled(true)))
      }
    }

    "enrol user" must {

      val nino = "WM123456C"

      "return a successful response" in {

        (htsConnector.enrolUser()(_: HeaderCarrier)).expects(*)
          .returning(EitherT.pure(Unit))

        val result = htsService.enrolUser()
        result.value.futureValue.isRight should be(true)
      }
    }

    "set ITMPFlag" must {

      val nino = "WM123456C"

      "return a successful response" in {

        (htsConnector.setITMPFlag()(_: HeaderCarrier)).expects(*)
          .returning(EitherT.pure(Unit))

        val result = htsService.setITMPFlag()
        result.value.futureValue.isRight should be(true)
      }
    }

    "store email" must {

      val nino = "WM123456C"
      val email = "user@test.com"

      "return a successful response" in {

        (htsConnector.storeEmail(_: String)(_: HeaderCarrier)).expects(email, *)
          .returning(EitherT.pure(Unit))

        val result = htsService.storeConfirmedEmail(email)
        result.value.futureValue.isRight should be(true)
      }
    }

    "get email" must {

      val nino = "WM123456C"

      "return a successful response" in {

        (htsConnector.getEmail()(_: HeaderCarrier)).expects(*)
          .returning(EitherT.pure(None))

        val result = htsService.getConfirmedEmail()
        result.value.futureValue.isRight should be(true)
      }
    }

    "checking eligibility" must {

      val nino = "WM123456C"

      "return a successful response if the connector returns a successful response" in {

        val eligibilityCheckResult = randomEligibilityCheckResult()

        (htsConnector.getEligibility()(_: HeaderCarrier)).expects(*)
          .returning(EitherT.pure(eligibilityCheckResult))

        val result = htsService.checkEligibility()
        result.value.futureValue should be(Right(eligibilityCheckResult))
      }

      "return an unsuccessful response if the connector returns an unsuccessful response" in {
        (htsConnector.getEligibility()(_: HeaderCarrier)).expects(*)
          .returning(EitherT.fromEither[Future](Left("uh oh")))

        val result = htsService.checkEligibility()
        result.value.futureValue should be(Left("uh oh"))
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
