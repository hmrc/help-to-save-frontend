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

import java.util.Base64

import cats.data.EitherT
import cats.instances.int._
import cats.instances.string._
import cats.syntax.eq._
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.libs.json._
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.config.WSHttp
import uk.gov.hmrc.helptosavefrontend.connectors.HelpToSaveConnectorImpl.{EligibilityCheckResponse, GetEmailResponse}
import uk.gov.hmrc.helptosavefrontend.models._
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

  implicit val unitFormat: Format[Unit] = new Format[Unit] {
    override def writes(o: Unit) = JsNull

    override def reads(json: JsValue) = json match {
      case JsNull ⇒ JsSuccess(())
      case _      ⇒ JsError("JSON was not null")
    }
  }

  val eligibleString = "Eligible to HtS Account"

  val ineligibleString = "Ineligible to HtS Account"

  "The HelpToSaveConnectorImpl" when {

    val nino = "nino"

    "getting eligibility status" should {

      behave like testCommon(
        mockHttpGet(eligibilityURL),
        () ⇒ connector.getEligibility(),
        EligibilityCheckResponse(eligibleString, EligibilityReason.UC.legibleString)
      )

      "return an EligibilityResult if the call comes back with a 200 status with a positive result " +
        "and a valid reason" in {
          EligibilityReason.reasons.map(_.legibleString).foreach { eligibilityReason ⇒
            mockHttpGet(eligibilityURL)(
              Some(HttpResponse(200, responseJson = Some(Json.toJson(EligibilityCheckResponse(eligibleString, eligibilityReason))))))

            val result = connector.getEligibility()
            await(result.value) shouldBe Right(
              EligibilityCheckResult(Right(
                EligibilityReason.fromString(eligibilityReason).getOrElse(sys.error(s"Could not get eligibility reason for $eligibilityReason"))
              )))
          }
        }

      "return an EligibilityResult if the call comes back with a 200 status with a negative result " +
        "and a valid reason" in {
          IneligibilityReason.reasons.map(_.legibleString).foreach { ineligibilityReason ⇒
            mockHttpGet(eligibilityURL)(
              Some(HttpResponse(200, responseJson = Some(Json.toJson(
                EligibilityCheckResponse(ineligibleString, ineligibilityReason))))))

            val result = connector.getEligibility()
            await(result.value) shouldBe Right(
              EligibilityCheckResult(Left(
                IneligibilityReason.fromString(ineligibilityReason).getOrElse(sys.error(s"Could not get ineligibility reason for $ineligibilityReason"))
              )))
          }
        }

      "return an error" when {
        "the call comes back with a 200 status with a positive result " +
          "and an invalid reason" in {
            forAll { eligibilityReason: String ⇒
              whenever(!EligibilityReason.reasons.map(_.legibleString).contains(eligibilityReason)) {
                mockHttpGet(eligibilityURL)(
                  Some(HttpResponse(200, responseJson = Some(Json.toJson(
                    EligibilityCheckResponse(eligibleString, eligibilityReason))))))

                val result = connector.getEligibility()
                await(result.value).isLeft shouldBe true
              }
            }
          }

        "the call comes back with a 200 status with a negative result " +
          "and an invalid reason" in {
            forAll { ineligibilityReason: String ⇒
              whenever(!IneligibilityReason.reasons.map(_.legibleString).contains(ineligibilityReason)) {
                mockHttpGet(eligibilityURL)(
                  Some(HttpResponse(200, responseJson = Some(Json.toJson(
                    EligibilityCheckResponse(ineligibleString, ineligibilityReason))))))

                val result = connector.getEligibility()
                await(result.value).isLeft shouldBe true
              }
            }
          }

        "the call comes back with a 200 and an unknown result" in {
          forAll { (result: String, reason: String) ⇒
            whenever(result =!= eligibleString && result =!= ineligibleString) {
              mockHttpGet(eligibilityURL)(
                Some(HttpResponse(200, responseJson = Some(Json.toJson(
                  EligibilityCheckResponse(result, reason))))))

              val r = connector.getEligibility()
              await(r.value).isLeft shouldBe true
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

  }

  private def testCommon[E, A, B](mockGet:         ⇒ Option[HttpResponse] ⇒ Unit,
                                  getResult:       () ⇒ EitherT[Future, E, A],
                                  validBody:       B,
                                  testInvalidJSON: Boolean                       = true)(implicit writes: Writes[B]) = { // scalstyle:ignore method.length
    "perform a GET request to the help-to-save-service" in {
      mockGet(Some(HttpResponse(200)))
      await(getResult())
    }

    "return an error" when {

      if (testInvalidJSON) {
        "the call comes back with a 200 and an unknown JSON format" in {
          mockGet(
            Some(HttpResponse(200, responseJson = Some(Json.parse(
              """
                |{
                |  "foo": "bar"
                |}
              """.
                stripMargin
            )))))

          await(getResult().value).isLeft shouldBe
            true
        }
      }

      "the call comes back with any other status other than 200" in {
        forAll { status: Int ⇒
          whenever(status =!= 200) {
            // check we get an error even though there was valid JSON in the response
            mockGet(Some(HttpResponse(status, Some(Json.toJson(validBody)))))
            await(getResult().value).isLeft shouldBe true

          }
        }
      }

      "the future fails" in {
        mockGet(None)
        await(getResult().value).isLeft shouldBe true
      }
    }
  }

}
