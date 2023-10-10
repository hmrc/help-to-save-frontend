/*
 * Copyright 2023 HM Revenue & Customs
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
import org.mockito.ArgumentMatchersSugar.*
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.Json
import uk.gov.hmrc.helptosavefrontend.connectors.HelpToSaveConnector
import uk.gov.hmrc.helptosavefrontend.controllers.ControllerSpecWithGuiceApp
import uk.gov.hmrc.helptosavefrontend.models.TestData.Eligibility.randomEligibility
import uk.gov.hmrc.helptosavefrontend.models.TestData.UserData.validNSIPayload
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.account.{Account, AccountNumber}
import uk.gov.hmrc.helptosavefrontend.models.register.CreateAccountRequest
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveServiceImpl.{SubmissionFailure, SubmissionSuccess}
import uk.gov.hmrc.http.HttpResponse

import java.time.LocalDate
import java.util.UUID
import scala.concurrent.Future

// scalastyle:off magic.number
class HelpToSaveServiceSpec extends ControllerSpecWithGuiceApp with ScalaFutures {

  val htsConnector = mock[HelpToSaveConnector]

  val htsService = new HelpToSaveServiceImpl(htsConnector)
  val emptyBody = ""
  val emptyHeaders: Map[String, Seq[String]] = Map.empty

  "The HelpToSaveService" when {

    "get user enrolment status" must {

      "return a successful response" in {

        htsConnector.getUserEnrolmentStatus()(*, *) returns EitherT.pure(EnrolmentStatus.Enrolled(true))

        val result = htsService.getUserEnrolmentStatus()
        result.value.futureValue should be(Right(EnrolmentStatus.Enrolled(true)))
      }
    }

    "set ITMPFlag" must {

      "return a successful response" in {

        htsConnector.setITMPFlagAndUpdateMongo()(*, *) returns EitherT.pure(())

        val result = htsService.setITMPFlagAndUpdateMongo()
        result.value.futureValue.isRight should be(true)
      }
    }

    "store email" must {

      val email = "user@test.com"

      "return a successful response" in {

        htsConnector.storeEmail(email)(*, *) returns EitherT.pure(())

        val result = htsService.storeConfirmedEmail(email)
        result.value.futureValue.isRight should be(true)
      }
    }

    "get email" must {

      "return a successful response" in {

        htsConnector.getEmail()(*, *) returns EitherT.pure(None)

        val result = htsService.getConfirmedEmail()
        result.value.futureValue.isRight should be(true)
      }
    }

    "checking eligibility" must {

      "return a successful response if the connector returns a successful response" in {

        val eligibilityCheckResult = randomEligibility()

        htsConnector.getEligibility()(*, *) returns EitherT.pure(eligibilityCheckResult)

        val result = htsService.checkEligibility()
        result.value.futureValue should be(Right(eligibilityCheckResult))
      }

      "return an unsuccessful response if the connector returns an unsuccessful response" in {
        htsConnector.getEligibility()(*, *) returns EitherT.fromEither[Future](Left("uh oh"))

        val result = htsService.checkEligibility()
        result.value.futureValue should be(Left("uh oh"))
      }
    }

    "createAccount" must {

      val createAccountRequest = CreateAccountRequest(validNSIPayload, 7)

      def mockCreateAccount(response: Option[HttpResponse]) =
        htsConnector.createAccount(createAccountRequest)(*, *) returns
          response.fold[Future[HttpResponse]](Future.failed(new Exception("oh no!")))(r => Future.successful(r))

      "return a CREATED response along with the account number when a new account has been created" in {
        mockCreateAccount(Some(HttpResponse(201, Json.parse("""{"accountNumber" : "1234567890123"}"""), emptyHeaders)))
        val result = htsService.createAccount(createAccountRequest)
        result.value.futureValue shouldBe Right(SubmissionSuccess(AccountNumber(Some("1234567890123"))))

      }

      "return a CONFLICT response with no Json when an account has already been created" in {
        mockCreateAccount(Some(HttpResponse(409, emptyBody)))
        val result = htsService.createAccount(createAccountRequest)
        result.value.futureValue shouldBe Right(SubmissionSuccess(AccountNumber(None)))
      }

      "should handle a failure result" in {

        val submissionFailure = SubmissionFailure(Some("id"), "message", "detail")
        mockCreateAccount(Some(HttpResponse(400, Json.toJson(submissionFailure), emptyHeaders)))

        val result = htsService.createAccount(createAccountRequest)
        result.value.futureValue should be(Left(submissionFailure))
      }

      "recover from unexpected errors" in {
        mockCreateAccount(None)

        val result = htsService.createAccount(createAccountRequest)
        result.value.futureValue should be(
          Left(SubmissionFailure(None, "Encountered error while trying to create account", "oh no!"))
        )
      }

      "create a SubmissionFailure when parsing the error response returns a Left" in {
        mockCreateAccount(Some(HttpResponse(400, Json.toJson("""{"name":"some_name"}"""), emptyHeaders)))

        val result = htsService.createAccount(createAccountRequest)
        result.value.futureValue shouldBe
          Left(
            SubmissionFailure(
              None,
              "",
              """Could not parse http response JSON: : [error.expected.jsobject]. Response body was "{\"name\":\"some_name\"}"}"""
            )
          )
      }
    }

    "update email" must {

      val nsiPayload = validNSIPayload

      def mockUpdateEmail(response: Option[HttpResponse]) =
        htsConnector.updateEmail(nsiPayload)(*, *) returns response.fold[Future[HttpResponse]](
          Future.failed(new Exception("oh no!"))
        )(r => Future.successful(r))

      "return a success response" in {

        mockUpdateEmail(Some(HttpResponse(200, emptyBody)))

        val result = htsService.updateEmail(nsiPayload)
        result.value.futureValue shouldBe Right(())

      }

      "handle failure response" in {
        mockUpdateEmail(Some(HttpResponse(400, null)))

        val result = htsService.updateEmail(nsiPayload)
        result.value.futureValue shouldBe Left(
          "Received unexpected status 400 from NS&I proxy while trying to update email. Body was null"
        )
      }

      "recover from unexpected errors" in {
        mockUpdateEmail(None)

        val result = htsService.updateEmail(nsiPayload)
        result.value.futureValue should be(Left("Encountered error while trying to update email: oh no!"))
      }
    }

    "checking if isAccountCreationAllowed" must {
      "return user cap response" in {
        htsConnector.isAccountCreationAllowed()(*, *) returns EitherT.pure(UserCapResponse())

        val result = htsService.isAccountCreationAllowed()
        result.value.futureValue should be(Right(UserCapResponse()))
      }
    }

    "get Account" must {
      "return a successful response" in {
        val nino = "WM123456C"
        val correlationId = UUID.randomUUID()
        val account =
          Account(false, 123.45, 0, 0, 0, LocalDate.parse("1900-01-01"), List(), None, None)

        htsConnector.getAccount(nino, correlationId)(*, *) returns EitherT.pure(account)

        val result = htsService.getAccount(nino, correlationId)
        result.value.futureValue should be(Right(account))
      }
    }

    "validateBankDetails" must {
      val request = ValidateBankDetailsRequest("AE123456C", "123456", "01023456")

      def mockValidateBankDetails(request: ValidateBankDetailsRequest)(response: HttpResponse) =
        htsConnector.validateBankDetails(request)(*, *) returns Future.successful(response)

      "return a successful response" in {
        mockValidateBankDetails(request)(
          HttpResponse(200, Json.parse("""{"isValid":true, "sortCodeExists":true}"""), emptyHeaders)
        )

        val result = htsService.validateBankDetails(request)
        result.value.futureValue should be(Right(ValidateBankDetailsResult(true, true)))
      }

      "handle failure response" in {
        mockValidateBankDetails(request)(HttpResponse(500, emptyBody))

        val result = htsService.validateBankDetails(request)
        result.value.futureValue.isLeft shouldBe true
      }
    }
  }
}
