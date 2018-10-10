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

package uk.gov.hmrc.helptosavefrontend.services

import java.time.LocalDate
import java.util.UUID

import cats.data.EitherT
import cats.instances.future._
import play.api.libs.json.{JsObject, JsValue, Json}
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.connectors.HelpToSaveConnector
import uk.gov.hmrc.helptosavefrontend.models.TestData.Eligibility.randomEligibility
import uk.gov.hmrc.helptosavefrontend.models.TestData.UserData.validNSIPayload
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.account.{Account, AccountNumber, Blocking}
import uk.gov.hmrc.helptosavefrontend.models.register.CreateAccountRequest
import uk.gov.hmrc.helptosavefrontend.models.userinfo.NSIPayload
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveServiceImpl.{SubmissionFailure, SubmissionSuccess}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

// scalastyle:off magic.number
class HelpToSaveServiceSpec extends TestSupport {

  val htsConnector = mock[HelpToSaveConnector]

  val htsService = new HelpToSaveServiceImpl(htsConnector)

  "The HelpToSaveService" when {

    "get user enrolment status" must {

      val nino = "WM123456C"

      "return a successful response" in {

        (htsConnector.getUserEnrolmentStatus()(_: HeaderCarrier, _: ExecutionContext)).expects(*, *)
          .returning(EitherT.pure(EnrolmentStatus.Enrolled(true)))

        val result = htsService.getUserEnrolmentStatus()
        result.value.futureValue should be(Right(EnrolmentStatus.Enrolled(true)))
      }
    }

    "set ITMPFlag" must {

      val nino = "WM123456C"

      "return a successful response" in {

        (htsConnector.setITMPFlagAndUpdateMongo()(_: HeaderCarrier, _: ExecutionContext)).expects(*, *)
          .returning(EitherT.pure(Unit))

        val result = htsService.setITMPFlagAndUpdateMongo()
        result.value.futureValue.isRight should be(true)
      }
    }

    "store email" must {

      val nino = "WM123456C"
      val email = "user@test.com"

      "return a successful response" in {

        (htsConnector.storeEmail(_: String)(_: HeaderCarrier, _: ExecutionContext)).expects(email, *, *)
          .returning(EitherT.pure(Unit))

        val result = htsService.storeConfirmedEmail(email)
        result.value.futureValue.isRight should be(true)
      }
    }

    "get email" must {

      val nino = "WM123456C"

      "return a successful response" in {

        (htsConnector.getEmail()(_: HeaderCarrier, _: ExecutionContext)).expects(*, *)
          .returning(EitherT.pure(None))

        val result = htsService.getConfirmedEmail()
        result.value.futureValue.isRight should be(true)
      }
    }

    "checking eligibility" must {

      val nino = "WM123456C"

      "return a successful response if the connector returns a successful response" in {

        val eligibilityCheckResult = randomEligibility()

        (htsConnector.getEligibility()(_: HeaderCarrier, _: ExecutionContext)).expects(*, *)
          .returning(EitherT.pure(eligibilityCheckResult))

        val result = htsService.checkEligibility()
        result.value.futureValue should be(Right(eligibilityCheckResult))
      }

      "return an unsuccessful response if the connector returns an unsuccessful response" in {
        (htsConnector.getEligibility()(_: HeaderCarrier, _: ExecutionContext)).expects(*, *)
          .returning(EitherT.fromEither[Future](Left("uh oh")))

        val result = htsService.checkEligibility()
        result.value.futureValue should be(Left("uh oh"))
      }
    }

    "createAccount" must {

      val createAccountRequest = CreateAccountRequest(validNSIPayload, 7)

        def mockCreateAccount(response: Option[HttpResponse]) = {
          (htsConnector.createAccount(_: CreateAccountRequest)(_: HeaderCarrier, _: ExecutionContext)).expects(createAccountRequest, *, *)
            .returning(response.fold[Future[HttpResponse]](Future.failed(new Exception("oh no!")))(r ⇒ Future.successful(r)))
        }

      "return a CREATED response along with the account number when a new account has been created" in {
        mockCreateAccount(Some(HttpResponse(201, Some(Json.parse("""{"accountNumber" : "1234567890123"}""")))))
        val result = htsService.createAccount(createAccountRequest)
        result.value.futureValue shouldBe Right(SubmissionSuccess(Some(AccountNumber("1234567890123"))))

      }

      "return a CONFLICT response with no Json when an account has already been created" in {
        mockCreateAccount(Some(HttpResponse(409, None)))
        val result = htsService.createAccount(createAccountRequest)
        result.value.futureValue shouldBe Right(SubmissionSuccess(None))
      }

      "should handle a failure result" in {

        val submissionFailure = SubmissionFailure(Some("id"), "message", "detail")
        mockCreateAccount(Some(HttpResponse(400, Some(Json.toJson(submissionFailure)))))

        val result = htsService.createAccount(createAccountRequest)
        result.value.futureValue should be(Left(submissionFailure))
      }

      "recover from unexpected errors" in {
        mockCreateAccount(None)

        val result = htsService.createAccount(createAccountRequest)
        result.value.futureValue should be(Left(SubmissionFailure(None, "Encountered error while trying to create account", "oh no!")))
      }

      "create a SubmissionFailure when parsing the error response returns a Left" in {
        mockCreateAccount(Some(HttpResponse(400, Some(Json.toJson("""{"name":"some_name"}""")))))

        val result = htsService.createAccount(createAccountRequest)
        result.value.futureValue shouldBe
          (Left(SubmissionFailure(None, "", """Could not parse http response JSON: /errorDetail: [error.path.missing]; /errorMessage: [error.path.missing]. Response body was "{\"name\":\"some_name\"}"}""")))
      }
    }

    "update email" must {

      val nsiPayload = validNSIPayload

        def mockUpdateEmail(response: Option[HttpResponse]) = {
          (htsConnector.updateEmail(_: NSIPayload)(_: HeaderCarrier, _: ExecutionContext)).expects(nsiPayload, *, *)
            .returning(response.fold[Future[HttpResponse]](Future.failed(new Exception("oh no!")))(r ⇒ Future.successful(r)))
        }

      "return a success response" in {

        mockUpdateEmail(Some(HttpResponse(200)))

        val result = htsService.updateEmail(nsiPayload)
        result.value.futureValue shouldBe Right(())

      }

      "handle failure response" in {
        mockUpdateEmail(Some(HttpResponse(400)))

        val result = htsService.updateEmail(nsiPayload)
        result.value.futureValue shouldBe Left("Received unexpected status 400 from NS&I proxy while trying to update email. Body was null")
      }

      "recover from unexpected errors" in {
        mockUpdateEmail(None)

        val result = htsService.updateEmail(nsiPayload)
        result.value.futureValue should be(Left("Encountered error while trying to update email: oh no!"))
      }
    }

    "checking if isAccountCreationAllowed" must {
      "return user cap response" in {
        (htsConnector.isAccountCreationAllowed()(_: HeaderCarrier, _: ExecutionContext)).expects(*, *)
          .returning(EitherT.pure(UserCapResponse()))

        val result = htsService.isAccountCreationAllowed()
        result.value.futureValue should be(Right(UserCapResponse()))
      }
    }

    "get Account" must {
      "return a successful response" in {
        val nino = "WM123456C"
        val correlationId = UUID.randomUUID()
        val account = Account(false, Blocking(false), 123.45, 0, 0, 0, LocalDate.parse("1900-01-01"), List(), None, None)

        (htsConnector.getAccount(_: String, _: UUID)(_: HeaderCarrier, _: ExecutionContext)).expects(nino, correlationId, *, *)
          .returning(EitherT.pure(account))

        val result = htsService.getAccount(nino, correlationId)
        result.value.futureValue should be(Right(account))
      }
    }
  }
}
