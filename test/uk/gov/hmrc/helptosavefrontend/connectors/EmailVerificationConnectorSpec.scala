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

package uk.gov.hmrc.helptosavefrontend.connectors

import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.http.Status
import play.api.libs.json.{JsValue, Writes}
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig.{accountHolderContinueURL, newApplicantContinueURL, verifyEmailURL}
import uk.gov.hmrc.helptosavefrontend.config.WSHttp
import uk.gov.hmrc.helptosavefrontend.models.VerifyEmailError.BackendError
import uk.gov.hmrc.helptosavefrontend.models.{EmailVerificationRequest, VerifyEmailError}
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, Email, NINO}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

class EmailVerificationConnectorSpec extends UnitSpec with TestSupport with GeneratorDrivenPropertyChecks {

  val nino: NINO = "AE123XXXX"
  val email: Email = "email@gmail.com"

  val mockHttp: WSHttp = mock[WSHttp]

  implicit val crypto: Crypto = mock[Crypto]

  def emailVerificationRequest(isNewApplicant: Boolean): EmailVerificationRequest =
    EmailVerificationRequest(
      email,
      "awrs_email_verification",
      "PT2H",
      if (isNewApplicant) s"$newApplicantContinueURL?p=" else s"$accountHolderContinueURL?p=",
      Map("email" → email, "nino" → nino))

  lazy val connector: EmailVerificationConnectorImpl =
    new EmailVerificationConnectorImpl(mockHttp, mockMetrics)

  def mockPost[A](expectedBody: A)(returnedStatus: Int, returnedData: Option[JsValue]): Unit = {
    (mockHttp.post(_: String, _: A, _: Seq[(String, String)])(_: Writes[A], _: HeaderCarrier, _: ExecutionContext))
      .expects(verifyEmailURL, expectedBody, Seq.empty[(String, String)], *, *, *)
      .returning(Future.successful(HttpResponse(returnedStatus, returnedData)))
  }

  def mockPostFailure[A](expectedBody: A): Unit = {
    (mockHttp.post(_: String, _: A, _: Seq[(String, String)])(_: Writes[A], _: HeaderCarrier, _: ExecutionContext))
      .expects(verifyEmailURL, expectedBody, Seq.empty[(String, String)], *, *, *)
      .returning(Future.failed(new Exception("Oh no!")))
  }

  def mockEncrypt(expected: String)(result: String): Unit =
    (crypto.encrypt(_: String)).expects(expected).returning(result)

  "verifyEmail" when {

    "handling new applicants" must {
      test(isNewApplicant = true)
    }

    "handling returning users" must {
      test(isNewApplicant = false)
    }

      def test(isNewApplicant: Boolean): Unit = { // scalastyle:ignore method.length

        val verificationRequest = emailVerificationRequest(isNewApplicant)

        "return 201 when given good json" in {
          mockEncrypt(nino + "#" + email)("")
          mockPost(verificationRequest)(Status.OK, None)
          await(connector.verifyEmail(nino, email, isNewApplicant)) shouldBe Right(())
        }

        "return 400 when given bad json" in {
          mockEncrypt(nino + "#" + email)("")
          mockPost(verificationRequest)(Status.BAD_REQUEST, None)
          await(connector.verifyEmail(nino, email, isNewApplicant)) shouldBe Left(VerifyEmailError.RequestNotValidError)
        }

        "return 409 when the email has already been verified" in {
          mockEncrypt(nino + "#" + email)("")
          mockPost(verificationRequest)(Status.CONFLICT, None)
          await(connector.verifyEmail(nino, email, isNewApplicant)) shouldBe Left(VerifyEmailError.AlreadyVerified)
        }

        "return a verification service unavailable error when the email verification service is down" in {
          mockEncrypt(nino + "#" + email)("")
          mockPost(verificationRequest)(Status.SERVICE_UNAVAILABLE, None)
          await(connector.verifyEmail(nino, email, isNewApplicant)) shouldBe Left(VerifyEmailError.VerificationServiceUnavailable)
        }

        "return a Left if the call comes back with an unexpected status" in {
          val statuses = Set(Status.OK, Status.CREATED, Status.BAD_REQUEST, Status.CONFLICT, Status.SERVICE_UNAVAILABLE)

          forAll { (status: Int) ⇒
            whenever(!statuses.contains(status)) {
              mockEncrypt(nino + "#" + email)("")
              mockPost(verificationRequest)(status, None)
              await(connector.verifyEmail(nino, email, isNewApplicant)) shouldBe Left(BackendError)
            }
          }
        }

        "should return a back end error if the future failed" in {
          mockEncrypt(nino + "#" + email)("")
          mockPostFailure(verificationRequest)
          await(connector.verifyEmail(nino, email, isNewApplicant)) shouldBe Left(BackendError)
        }
      }
  }
}
