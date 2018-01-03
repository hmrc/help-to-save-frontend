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

package uk.gov.hmrc.helptosavefrontend.connectors

import java.util.Base64

import cats.data.EitherT
import cats.instances.int._
import cats.syntax.eq._
import org.scalacheck.Gen
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.libs.json._
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.config.WSHttp
import uk.gov.hmrc.helptosavefrontend.connectors.HelpToSaveConnectorImpl.{ECResponseHolder, GetEmailResponse}
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityCheckResult.{AlreadyHasAccount, Eligible, Ineligible}
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityCheckResponse
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

// scalastyle:off magic.number
class HelpToSaveConnectorSpec extends TestSupport with GeneratorDrivenPropertyChecks {

  import uk.gov.hmrc.helptosavefrontend.connectors.HelpToSaveConnectorImpl.URLS._

  val mockHttp: WSHttp = mock[WSHttp]

  lazy val connector: HelpToSaveConnector = new HelpToSaveConnectorImpl(mockHttp)

  def mockHttpGet[I](url: String)(result: Option[HttpResponse]): Unit =
    (mockHttp.get(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(url, *, *)
      .returning(result.fold(
        Future.failed[HttpResponse](new Exception("")))(Future.successful))

  def mockHttpPost[A](url: String, body: A)(result: Option[HttpResponse]): Unit =
    (mockHttp.post(_: String, _: A, _: Seq[(String, String)])(_: Writes[A], _: HeaderCarrier, _: ExecutionContext))
      .expects(url, body, Seq.empty[(String, String)], *, *, *)
      .returning(result.fold(
        Future.failed[HttpResponse](new Exception("")))(Future.successful))

  implicit val unitFormat: Format[Unit] = new Format[Unit] {
    override def writes(o: Unit) = JsNull

    override def reads(json: JsValue) = json match {
      case JsNull ⇒ JsSuccess(())
      case _      ⇒ JsError("JSON was not null")
    }
  }

  val eligibleString = "Eligible to HtS Account"

  val ineligibleString = "Ineligible to HtS Account"

  val eligibleResponseGen: Gen[EligibilityCheckResponse] =
    for {
      result ← Gen.alphaStr
      reasonCode ← Gen.choose(6, 8)
      reason ← Gen.alphaStr
    } yield EligibilityCheckResponse(result, 1, reason, reasonCode)

  val ineligibleResponseGen: Gen[EligibilityCheckResponse] =
    for {
      result ← Gen.alphaStr
      reasonCode ← Gen.choose(2, 5)
      reason ← Gen.alphaStr
    } yield EligibilityCheckResponse(result, 2, reason, reasonCode)

  "The HelpToSaveConnectorImpl" when {

    val nino = "nino"

    "getting eligibility status" should {

      behave like testCommon(
        mockHttpGet(eligibilityURL),
        () ⇒ connector.getEligibility(),
        ECResponseHolder(Some(EligibilityCheckResponse("eligible!", 1, "???", 6)))
      )

      "return an EligibilityResult if the call comes back with a 200 status with a positive result " +
        "and a valid reason" in {
          forAll(eligibleResponseGen){ response ⇒
            val reason = Eligible(response)

            mockHttpGet(eligibilityURL)(Some(HttpResponse(200, responseJson = Some(Json.toJson(ECResponseHolder(Some(response)))))))

            val result = connector.getEligibility()
            await(result.value) shouldBe Right(reason)
          }
        }

      "return an EligibilityResult if the call comes back with a 200 status with a negative result " +
        "and a valid reason" in {
          forAll(ineligibleResponseGen){ response ⇒
            val reason = Ineligible(response)

            mockHttpGet(eligibilityURL)(Some(HttpResponse(200, responseJson = Some(Json.toJson(ECResponseHolder(Some(response)))))))

            val result = connector.getEligibility()
            await(result.value) shouldBe Right(reason)
          }
        }

      "return an EligibilityResult if the call comes back with a 200 status with a result " +
        "indicating an account has already been opened" in {
          val reasonString = "already has account"

          val response = EligibilityCheckResponse("HtS account already exists", 3, reasonString, 1)

          mockHttpGet(eligibilityURL)(Some(HttpResponse(200, responseJson = Some(Json.toJson(ECResponseHolder(Some(response)))))))

          val result = connector.getEligibility()
          await(result.value) shouldBe Right(AlreadyHasAccount(response))
        }

      "return an Ineligible when the given nino was not found to be in receipt of tax credit" in {
        val response = EligibilityCheckResponse("No tax credit record found for user's NINO", 2, "", -1)

        mockHttpGet(eligibilityURL)(Some(HttpResponse(200, responseJson = Some(Json.toJson(ECResponseHolder(None))))))

        val result = connector.getEligibility()
        await(result.value) shouldBe Right(Ineligible(response))
      }

      "return an error" when {
        "the call comes back with a 200 and an unknown result code" in {
          forAll { (resultCode: Int) ⇒
            whenever(!(1 to 3).contains(resultCode)) {
              mockHttpGet(eligibilityURL)(
                Some(HttpResponse(200, responseJson = Some(Json.toJson(ECResponseHolder(Some(
                  EligibilityCheckResponse("", resultCode, "", 1))))))))

              val result = connector.getEligibility()
              await(result.value).isLeft shouldBe true
            }
          }
        }

      }
    }

    "getting enrolment status" must {

      implicit val enrolmentStatusWrites: Writes[EnrolmentStatus] = new Writes[EnrolmentStatus] {

        case class EnrolledJSON(enrolled: Boolean = true, itmpHtSFlag: Boolean)

        case class NotEnrolledJSON(enrolled: Boolean = false)

        implicit val enrolledWrites: Writes[EnrolledJSON] = Json.writes[EnrolledJSON]
        implicit val notEnrolledFormat: Writes[NotEnrolledJSON] = Json.writes[NotEnrolledJSON]

        override def writes(o: EnrolmentStatus) = o match {
          case EnrolmentStatus.Enrolled(itmpHtSFlag) ⇒ Json.toJson(EnrolledJSON(itmpHtSFlag = itmpHtSFlag))
          case EnrolmentStatus.NotEnrolled           ⇒ Json.toJson(NotEnrolledJSON())
        }

      }

      behave like testCommon(
        mockHttpGet(enrolmentStatusURL),
        () ⇒ connector.getUserEnrolmentStatus(),
        EnrolmentStatus.NotEnrolled
      )

      "return a Right if the call comes back with HTTP status 200 with " +
        "valid JSON in the body" in {
          mockHttpGet(enrolmentStatusURL)(Some(HttpResponse(
            200,
            Some(Json.parse(
              """
              |{
              |  "enrolled"    : true,
              |  "itmpHtSFlag" : true
              |}
            """.stripMargin))
          )))

          await(connector.getUserEnrolmentStatus().value) shouldBe Right(
            EnrolmentStatus.Enrolled(itmpHtSFlag = true))

          mockHttpGet(enrolmentStatusURL)(Some(HttpResponse(
            200,
            Some(Json.parse(
              """
              |{
              |  "enrolled"    : true,
              |  "itmpHtSFlag" : false
              |}
            """.stripMargin))
          )))

          await(connector.getUserEnrolmentStatus().value) shouldBe Right(
            EnrolmentStatus.Enrolled(itmpHtSFlag = false))

          mockHttpGet(enrolmentStatusURL)(Some(HttpResponse(
            200,
            Some(Json.parse(
              """
              |{
              |  "enrolled" : false,
              |  "itmpHtSFlag" : false
              |}
            """.stripMargin))
          )))

          await(connector.getUserEnrolmentStatus().value) shouldBe Right(EnrolmentStatus.NotEnrolled)
        }

    }

    "enrolling a user" must {

      behave like testCommon(
        mockHttpGet(enrolUserURL),
        () ⇒ connector.enrolUser(),
        (),
        testInvalidJSON = false
      )

      "return a Right if the call comes back with HTTP status 200 with " +
        "valid JSON in the body" in {
          mockHttpGet(enrolUserURL)(Some(HttpResponse(200)))

          await(connector.enrolUser().value) shouldBe Right(())
        }

    }

    "setting the ITMP flag" must {

      behave like testCommon(
        mockHttpGet(setITMPFlagURL),
        () ⇒ connector.setITMPFlag(),
        (),
        testInvalidJSON = false
      )

      "return a Right if the call comes back with HTTP status 200 with " +
        "valid JSON in the body" in {
          mockHttpGet(setITMPFlagURL)(Some(HttpResponse(200)))

          await(connector.setITMPFlag().value) shouldBe Right(())
        }
    }

    "storing emails" must {

      val email = "email"

      val encodedEmail = new String(Base64.getEncoder.encode(email.getBytes()))
      behave like testCommon(
        mockHttpGet(storeEmailURL(encodedEmail)),
        () ⇒ connector.storeEmail(email),
        (),
        testInvalidJSON = false
      )

      "return a Right if the call comes back with HTTP status 200 with " +
        "valid JSON in the body" in {
          mockHttpGet(storeEmailURL(encodedEmail))(Some(HttpResponse(200)))

          await(connector.storeEmail(email).value) shouldBe Right(())
        }
    }

    "getting emails" must {

      val nino = "nino"

      val email = "email"

      behave like testCommon(
        mockHttpGet(getEmailURL),
        () ⇒ connector.getEmail(),
        GetEmailResponse(None),
        false
      )

      "return a Right if the call comes back with HTTP status 200 and " +
        "valid JSON in the body" in {
          mockHttpGet(getEmailURL)(Some(HttpResponse(200, Some(Json.toJson(GetEmailResponse(Some(email)))))))
          await(connector.getEmail().value) shouldBe Right(Some(email))

          mockHttpGet(getEmailURL)(Some(HttpResponse(200, Some(Json.toJson(GetEmailResponse(None))))))
          await(connector.getEmail().value) shouldBe Right(None)
        }
    }

    "getting account-creation-allowed response" must {
      behave like testCommon(
        mockHttpGet(accountCreateAllowedURL),
        () ⇒ connector.isAccountCreationAllowed(),
        Json.toJson(true),
        true
      )

      "return a Right if the call comes with HTTP 200 and valid response in the body" in {
        mockHttpGet(accountCreateAllowedURL)(Some(HttpResponse(200, Some(Json.toJson(UserCapResponse())))))
        await(connector.isAccountCreationAllowed().value) shouldBe Right(UserCapResponse())
      }
    }

    "updating user-cap-count" must {

      behave like testCommon(
        mockHttpPost(updateUserCountURL, ""),
        () ⇒ connector.updateUserCount(),
        Json.toJson(()),
        false
      )

      "return a Right if the call comes with with HTTP 200" in {
        mockHttpPost(updateUserCountURL, "")(Some(HttpResponse(200, Some(Json.toJson(())))))
        await(connector.updateUserCount().value) shouldBe Right(())
      }
    }

  }

  private def testCommon[E, A, B](mockHttp:        ⇒ Option[HttpResponse] ⇒ Unit,
                                  result:          () ⇒ EitherT[Future, E, A],
                                  validBody:       B,
                                  testInvalidJSON: Boolean                       = true)(implicit writes: Writes[B]) = { // scalstyle:ignore method.length
    "perform a GET request to the help-to-save-service" in {
      mockHttp(Some(HttpResponse(200)))
      await(result())
    }

    "return an error" when {

      if (testInvalidJSON) {
        "the call comes back with a 200 and an unknown JSON format" in {
          mockHttp(
            Some(HttpResponse(200, responseJson = Some(Json.parse(
              """
                |{
                |  "foo": "bar"
                |}
              """.
                stripMargin
            )))))

          await(result().value).isLeft shouldBe
            true
        }
      }

      "the call comes back with any other status other than 200" in {
        forAll { status: Int ⇒
          whenever(status =!= 200) {
            // check we get an error even though there was valid JSON in the response
            mockHttp(Some(HttpResponse(status, Some(Json.toJson(validBody)))))
            await(result().value).isLeft shouldBe true

          }
        }
      }

      "the future fails" in {
        mockHttp(None)
        await(result().value).isLeft shouldBe true
      }
    }
  }

}
