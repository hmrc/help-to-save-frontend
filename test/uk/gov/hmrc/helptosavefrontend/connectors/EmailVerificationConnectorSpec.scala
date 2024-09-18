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

package uk.gov.hmrc.helptosavefrontend.connectors
import com.typesafe.config.ConfigFactory
import org.mockito.IdiomaticMockito
import org.scalatest.EitherValues
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.{Application, Configuration}
import play.api.libs.json._
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.controllers.{ControllerSpecBase, ControllerSpecWithGuiceApp}
import uk.gov.hmrc.helptosavefrontend.models.email.VerifyEmailError.OtherError
import uk.gov.hmrc.helptosavefrontend.models.email.{EmailVerificationRequest, VerifyEmailError}
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, Email, NINO, NINOLogMessageTransformer, TestNINOLogMessageTransformer, WireMockMethods}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.http.test.WireMockSupport

import uk.gov.hmrc.http.HttpClient
import scala.concurrent.ExecutionContext

class EmailVerificationConnectorSpec
    extends ControllerSpecBase with IdiomaticMockito with WireMockSupport with WireMockMethods with GuiceOneAppPerSuite
    with EitherValues with ScalaCheckDrivenPropertyChecks {

  val nino: NINO = "AE123XXXX"
  val name: String = "first-name"
  val email: Email = "email@gmail.com"

  private val config = Configuration(
    ConfigFactory.parseString(
      s"""
         |microservice {
         |  services {
         |      email-verification {
         |      protocol = http
         |      host     = $wireMockHost
         |      port     = $wireMockPort
         |    }
         |  }
         |}
         |""".stripMargin
    )
  )

  implicit val crypto: Crypto = mock[Crypto]

  implicit val ninoLogMessageTransformer: NINOLogMessageTransformer = TestNINOLogMessageTransformer.transformer

  override def fakeApplication(): Application = new GuiceApplicationBuilder().configure(config).build()

  implicit lazy val appConfig: FrontendAppConfig = app.injector.instanceOf[FrontendAppConfig]

  val mockHttp: HttpClient = app.injector.instanceOf[HttpClient]
  lazy val connector: EmailVerificationConnectorImpl = new EmailVerificationConnectorImpl(mockHttp, mockMetrics)

  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit lazy val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

  def emailVerificationRequest(isNewApplicant: Boolean): EmailVerificationRequest =
    EmailVerificationRequest(
      email,
      "hts_verification_email",
      "PT2H",
      if (isNewApplicant) s"${appConfig.newApplicantContinueURL}?p=" else s"${appConfig.accountHolderContinueURL}?p=",
      Map("name" -> name)
    )

  val verifyEmailUrl = "/email-verification/verification-requests"
  def mockEncrypt(expected: String)(result: String): Unit = crypto.encrypt(expected) returns result

  "verifyEmail" when {

    "handling new applicants" must {
      test(isNewApplicant = true)
    }

    "handling returning users" must {
      test(isNewApplicant = false)
    }

    def test(isNewApplicant: Boolean): Unit = { // scalastyle:ignore method.length

      val verificationRequest = emailVerificationRequest(isNewApplicant)

      "return a success when given good json" in {
        mockEncrypt(nino + "#" + email)("")
        val response = HttpResponse(Status.OK, "")
        when(
          POST,
          verifyEmailUrl,
          body = Some(Json.toJson(verificationRequest).toString())
        ).thenReturn(response.status, response.body)
        await(connector.verifyEmail(nino, email, name, isNewApplicant)) shouldBe Right(())
      }

      "indicate the email has already been verified when the email has already been verified" in {
        mockEncrypt(nino + "#" + email)("")
        val response = HttpResponse(Status.CONFLICT, "")
        when(
          POST,
          verifyEmailUrl,
          body = Some(Json.toJson(verificationRequest).toString())
        ).thenReturn(response.status, response.body)

        await(connector.verifyEmail(nino, email, name, isNewApplicant)) shouldBe Left(VerifyEmailError.AlreadyVerified)
      }

      "return an error" when {

        "given bad json" in {
          mockEncrypt(nino + "#" + email)("")
          val response = HttpResponse(Status.BAD_REQUEST, "")
          when(
            POST,
            verifyEmailUrl,
            body = Some(Json.toJson(verificationRequest).toString())
          ).thenReturn(response.status, response.body)

          await(connector.verifyEmail(nino, email, name, isNewApplicant)) shouldBe Left(VerifyEmailError.OtherError)
        }

        "the email verification service is down" in {

          val response = HttpResponse(Status.SERVICE_UNAVAILABLE, "")
          mockEncrypt(nino + "#" + email)("")
          when(POST, verifyEmailUrl, body = Some(Json.toJson(verificationRequest).toString()))
            .thenReturn(response.status, response.body)
          await(connector.verifyEmail(nino, email, name, isNewApplicant)) shouldBe Left(VerifyEmailError.OtherError)
        }

        "the call comes back with an unexpected status" in {
          val allOtherStatuses = Set(Status.INTERNAL_SERVER_ERROR)
          val statuses = Set(Status.OK, Status.CREATED, Status.BAD_REQUEST, Status.CONFLICT, Status.SERVICE_UNAVAILABLE)

          allOtherStatuses.foreach { status: Int =>
            whenever(!statuses.contains(status)) {
              mockEncrypt(nino + "#" + email)("")
              when(POST, verifyEmailUrl, body = Some(Json.toJson(verificationRequest).toString()))
                .thenReturn(status, "")
              await(connector.verifyEmail(nino, email, name, isNewApplicant)) shouldBe Left(OtherError)
            }
          }
        }

        "the future fails" in {
          mockEncrypt(nino + "#" + email)("")
          wireMockServer.stop()
          when(POST, verifyEmailUrl, body = Some(Json.toJson(verificationRequest).toString()))
          val result = await(connector.verifyEmail(nino, email, name, isNewApplicant))
          result shouldBe Left(OtherError)
          wireMockServer.start()
        }
      }

    }
  }
}
