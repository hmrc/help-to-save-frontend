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
import org.scalacheck.Gen
import org.scalatest.EitherValues
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json._
import play.api.{Application, Configuration}
import uk.gov.hmrc.helptosavefrontend.connectors.HelpToSaveConnectorImpl.GetEmailResponse
import uk.gov.hmrc.helptosavefrontend.controllers.ControllerSpecBase
import uk.gov.hmrc.helptosavefrontend.models.TestData.UserData.validNSIPayload
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.account.{Account, AccountNumber}
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityCheckResultType.{AlreadyHasAccount, Eligible, Ineligible}
import uk.gov.hmrc.helptosavefrontend.models.eligibility.{EligibilityCheckResponse, EligibilityCheckResult}
import uk.gov.hmrc.helptosavefrontend.models.register.CreateAccountRequest
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveServiceImpl.SubmissionSuccess
import uk.gov.hmrc.helptosavefrontend.util.WireMockMethods
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import java.time.LocalDate
import java.util.{Base64, UUID}
import scala.concurrent.ExecutionContext

// scalastyle:off magic.number
class HelpToSaveConnectorSpec
    extends ControllerSpecBase with IdiomaticMockito with WireMockSupport with WireMockMethods with GuiceOneAppPerSuite
    with EitherValues with ScalaCheckDrivenPropertyChecks {

  private val config = Configuration(
    ConfigFactory.parseString(
      s"""
         |microservice {
         |  services {
         |      help-to-save {
         |      protocol = http
         |      host     = $wireMockHost
         |      port     = $wireMockPort
         |    }
         |  }
         |}
         |""".stripMargin
    )
  )

  override def fakeApplication(): Application = new GuiceApplicationBuilder().configure(config).build()
  lazy val connector: HelpToSaveConnector = app.injector.instanceOf[HelpToSaveConnectorImpl]
  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit lazy val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

  val eligibilityURL = "/help-to-save/eligibility-check"

  val enrolmentStatusURL = "/help-to-save/enrolment-status"

  val enrolUserURL = "/help-to-save/enrol-user"

  val setITMPFlagURL = "/help-to-save/set-itmp-flag"

  val storeEmailURL = "/help-to-save/store-email"

  val getEmailURL = "/help-to-save/get-email"

  val accountCreateAllowedURL = "/help-to-save/account-create-allowed"

  val updateUserCountURL = "/help-to-save/update-user-count"

  private val createAccountURL = "/help-to-save/create-account"

  private val updateEmailURL = "/help-to-save/update-email"

  private val validateBankDetailsURL = "/help-to-save/validate-bank-details"

  private val emptyHeaders: Map[String, Seq[String]] = Map.empty

  implicit val unitFormat: Format[Unit] = new Format[Unit] {
    override def writes(o: Unit) = JsNull

    override def reads(json: JsValue) = json match {
      case JsNull => JsSuccess(())
      case _      => JsError("JSON was not null")
    }
  }

  val eligibleString = "Eligible to HtS Account"

  val ineligibleString = "Ineligible to HtS Account"

  val eligibleResponseGen: Gen[EligibilityCheckResult] =
    for {
      result     <- Gen.alphaStr
      reasonCode <- Gen.choose(6, 8)
      reason     <- Gen.alphaStr
    } yield EligibilityCheckResult(result, 1, reason, reasonCode)

  val ineligibleResponseGen: Gen[EligibilityCheckResult] =
    for {
      result     <- Gen.alphaStr
      resultCode <- Gen.oneOf(Seq(2, 4))
      reasonCode <- Gen.choose(2, 5)
      reason     <- Gen.alphaStr
    } yield EligibilityCheckResult(result, resultCode, reason, reasonCode)

  "The HelpToSaveConnectorImpl" when {

    val nino = "nino"

    "getting eligibility status" should {

      "return an EligibilityResult if the call comes back with a 200 status with a positive result " +
        "and a valid reason" in {
        forAll(eligibleResponseGen) { response =>
          val reason = Eligible(EligibilityCheckResponse(response, Some(123.45)))
          val httpResponse =
            HttpResponse(200, Json.toJson(EligibilityCheckResponse(response, Some(123.45))), emptyHeaders)
          when(GET, eligibilityURL).thenReturn(httpResponse.status, httpResponse.body)
          val result = connector.getEligibility()
          await(result.value) shouldBe Right(reason)
        }
      }

      "return an EligibilityResult if the call comes back with a 200 status with a negative result " +
        "and a valid reason" in {
        forAll(ineligibleResponseGen) { response =>
          val reason =
            Ineligible(EligibilityCheckResponse(response, Some(123.45)))
          val httpResponse =
            HttpResponse(200, Json.toJson(EligibilityCheckResponse(response, Some(123.45))), emptyHeaders)
          when(GET, eligibilityURL).thenReturn(httpResponse.status, httpResponse.body)
          val result = connector.getEligibility()
          await(result.value) shouldBe Right(reason)
        }
      }

      "return an EligibilityResult if the call comes back with a 200 status with a result " +
        "indicating an account has already been opened" in {
        val reasonString = "already has account"

        val response = EligibilityCheckResult("HtS account already exists", 3, reasonString, 1)

        val httpResponse =
          HttpResponse(200, Json.toJson(EligibilityCheckResponse(response, Some(123.45))), emptyHeaders)
        when(GET, eligibilityURL).thenReturn(httpResponse.status, httpResponse.body)
        val result = connector.getEligibility()
        await(result.value) shouldBe Right(AlreadyHasAccount(EligibilityCheckResponse(response, Some(123.45))))
      }

      "return an error" when {

        def testError(resultCode: Int): Unit = {

          val httpResponse = HttpResponse(
            200,
            Json.toJson(EligibilityCheckResult("", resultCode, "", 1)),
            emptyHeaders
          )
          when(GET, eligibilityURL).thenReturn(httpResponse.status, httpResponse.body)
          val result = connector.getEligibility()
          await(result.value).isLeft shouldBe true
        }

        "the call comes back with a 200 and an unknown result code" in {
          forAll { resultCode: Int =>
            whenever(!(1 to 3).contains(resultCode)) {
              testError(resultCode)
            }
          }
        }

        "the future fails" in {
          wireMockServer.stop()
          when(GET, eligibilityURL)
          val result = connector.getEligibility()
          await(result.value).isLeft shouldBe true
          wireMockServer.start()
        }

      }
    }

    "getting enrolment status" must {

      "return a Right if the call comes back with HTTP status 200 with " +
        "valid JSON in the body" in {
        val response = HttpResponse(
          200,
          Json.parse("""
                       |{
                       |  "enrolled"    : true,
                       |  "itmpHtSFlag" : true
                       |}
            """.stripMargin),
          emptyHeaders
        )
        when(GET, enrolmentStatusURL).thenReturn(response.status, response.body)

        await(connector.getUserEnrolmentStatus().value) shouldBe Right(EnrolmentStatus.Enrolled(itmpHtSFlag = true))

        val enrolledResponse = HttpResponse(
          200,
          Json.parse("""
                       |{
                       |  "enrolled"    : true,
                       |  "itmpHtSFlag" : false
                       |}

            """.stripMargin),
          emptyHeaders
        )
        when(GET, enrolmentStatusURL).thenReturn(enrolledResponse.status, enrolledResponse.body)

        await(connector.getUserEnrolmentStatus().value) shouldBe Right(EnrolmentStatus.Enrolled(itmpHtSFlag = false))

        val notEnrolledResponse = HttpResponse(
          200,
          Json.parse("""
                       |{
                       |  "enrolled" : false,
                       |  "itmpHtSFlag" : false
                       |}
            """.stripMargin),
          emptyHeaders
        )
        when(GET, enrolmentStatusURL).thenReturn(notEnrolledResponse.status, notEnrolledResponse.body)

        await(connector.getUserEnrolmentStatus().value) shouldBe Right(EnrolmentStatus.NotEnrolled)
      }

      "the future fails" in {
        wireMockServer.stop()
        when(GET, enrolmentStatusURL)
        val result = connector.getUserEnrolmentStatus()
        await(result.value).isLeft shouldBe true
        wireMockServer.start()
      }

    }

    "setting the ITMP flag" must {

      "return a Right if the call comes back with HTTP status 200 with " +
        "valid JSON in the body" in {
        when(GET, setITMPFlagURL).thenReturn(200, "")
        await(connector.setITMPFlagAndUpdateMongo().value) shouldBe Right(())
      }

      "the future fails" in {
        wireMockServer.stop()
        when(GET, setITMPFlagURL)
        val result = connector.setITMPFlagAndUpdateMongo()
        await(result.value).isLeft shouldBe true
        wireMockServer.start()
      }
    }

    "storing emails" must {

      val email = "email"

      val encodedEmail = new String(Base64.getEncoder.encode(email.getBytes()))
      "return a Right if the call comes back with HTTP status 200 with " +
        "valid JSON in the body" in {
        when(GET, storeEmailURL, queryParams = Map("email" -> encodedEmail)).thenReturn(200, "")
        await(connector.storeEmail(email).value) shouldBe Right(())
      }
      "the future fails" in {
        wireMockServer.stop()
        when(GET, storeEmailURL)
        val result = connector.storeEmail(email)
        await(result.value).isLeft shouldBe true
        wireMockServer.start()
      }
    }

    "getting emails" must {

      val email = "email"

      "return a Right if the call comes back with HTTP status 200 and " +
        "valid JSON in the body" in {
        val validResponse = HttpResponse(200, Json.toJson(GetEmailResponse(Some(email))), emptyHeaders)
        when(GET, getEmailURL).thenReturn(validResponse.status, validResponse.body)
        await(connector.getEmail().value) shouldBe Right(Some(email))

        val inValidResponse = HttpResponse(200, Json.toJson(GetEmailResponse(None)), emptyHeaders)
        when(GET, getEmailURL).thenReturn(inValidResponse.status, inValidResponse.body)
        await(connector.getEmail().value) shouldBe Right(None)
      }

      "the future fails" in {
        wireMockServer.stop()
        when(GET, getEmailURL)
        val result = connector.getEmail()
        await(result.value).isLeft shouldBe true
        wireMockServer.start()
      }
    }

    "getting account-creation-allowed response" must {

      "return a Right if the call comes with HTTP 200 and valid response in the body" in {
        val response = HttpResponse(200, Json.toJson(UserCapResponse()), emptyHeaders)
        when(GET, accountCreateAllowedURL).thenReturn(response.status, response.body)
        await(connector.isAccountCreationAllowed().value) shouldBe Right(UserCapResponse())
      }

      "the future fails" in {
        wireMockServer.stop()
        when(GET, accountCreateAllowedURL)
        val result = connector.isAccountCreationAllowed()
        await(result.value).isLeft shouldBe true
        wireMockServer.start()
      }

    }

    "getting Account" must {

      val correlationId = UUID.randomUUID()
      val url = s"/help-to-save/$nino/account"
      val queryParameters = Map("correlationId" -> correlationId.toString, "systemId" -> "help-to-save-frontend")
      val account = Account(false, 123.45, 0, 0, 0, LocalDate.parse("1900-01-01"), List(), None, None)

      "be able to handle 200 responses with valid Account json" in {
        val response = HttpResponse(200, Json.toJson(account), emptyHeaders)
        when(GET, url, queryParameters, Map.empty).thenReturn(response.status, response.body)
        await(connector.getAccount(nino, correlationId).value) shouldBe Right(account)
      }

      "the future fails" in {
        wireMockServer.stop()
        when(GET, url, queryParams = queryParameters)
        val result = connector.getAccount(nino, correlationId)
        await(result.value).isLeft shouldBe true
        wireMockServer.start()
      }
    }

    "creating account" must {
      val request = CreateAccountRequest(validNSIPayload, 7)
      "return http response as it is to the caller" in {
        val response = HttpResponse(
          201,
          Json.toJson(SubmissionSuccess(AccountNumber(Some("1234567890123")))),
          emptyHeaders
        )
        when(POST, createAccountURL, body = Some(Json.toJson(request).toString()))
          .thenReturn(response.status, response.body)
        await(connector.createAccount(request)).status shouldBe response.status
      }

    }

    "update email" must {
      "return http response as it is to the caller" in {
        val response = HttpResponse(
          200,
          Json.toJson(()),
          emptyHeaders
        )
        when(PUT, updateEmailURL, body = Some(Json.toJson(validNSIPayload).toString()))
          .thenReturn(response.status, response.body)

        await(connector.updateEmail(validNSIPayload)).status shouldBe response.status
      }
    }

    "validating bank details" must {
      "return http response as it is to the caller" in {
        val response =
          HttpResponse(200, Json.parse("""{"isValid":true}"""), emptyHeaders)
        when(
          POST,
          validateBankDetailsURL,
          body = Some(Json.toJson(ValidateBankDetailsRequest(nino, "123456", "02012345")).toString())
        ).thenReturn(response.status, response.body)
        await(connector.validateBankDetails(ValidateBankDetailsRequest(nino, "123456", "02012345"))).status shouldBe response.status
      }
    }
  }

}
