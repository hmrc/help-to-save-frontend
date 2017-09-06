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
import cats.syntax.eq._
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.libs.json._
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.config.WSHttp
import uk.gov.hmrc.helptosavefrontend.connectors.HelpToSaveConnectorImpl.EligibilityCheckResponse
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.Future

// scalastyle:off magic.number
class HelpToSaveConnectorSpec extends TestSupport with GeneratorDrivenPropertyChecks {

  import uk.gov.hmrc.helptosavefrontend.connectors.HelpToSaveConnectorImpl.URLS._

  val mockHttp: WSHttp = mock[WSHttp]

  lazy val connector: HelpToSaveConnector = new HelpToSaveConnectorImpl(mockHttp)

  def mockHttpGet[I](url: String)(result: Option[HttpResponse]): Unit =
    (mockHttp.get(_: String)(_: HeaderCarrier))
      .expects(url, *)
      .returning(result.fold(
        Future.failed[HttpResponse](new Exception("")))(Future.successful))

  implicit val unitFormat: Format[Unit] = new Format[Unit] {
    override def writes(o: Unit) = JsNull

    override def reads(json: JsValue) = json match {
      case JsNull ⇒ JsSuccess(())
      case _      ⇒ JsError("JSON was not null")
    }
  }

  "The HelpToSaveConnectorImpl" when {

    val nino = "nino"

    "getting eligibility status" should {

      behave like testCommon(
        mockHttpGet(eligibilityURL(nino)),
        () ⇒ connector.getEligibility(nino),
        EligibilityCheckResponse(2, 1)
      )

      "return an EligibilityResult if the call comes back with a 200 status with a positive result " +
        "and a valid reason" in {
          (6 to 8).foreach { eligibilityReason ⇒
            mockHttpGet(eligibilityURL(nino))(
              Some(HttpResponse(200, responseJson = Some(Json.toJson(EligibilityCheckResponse(1, eligibilityReason))))))

            val result = connector.getEligibility(nino)
            await(result.value) shouldBe Right(
              EligibilityCheckResult(Right(
                EligibilityReason.fromInt(eligibilityReason).getOrElse(sys.error(s"Could not get eligibility reason for $eligibilityReason"))
              )))
          }
        }

      "return an EligibilityResult if the call comes back with a 200 status with a negative result " +
        "and a valid reason" in {
          (1 to 5).foreach { ineligibilityReason ⇒
            mockHttpGet(eligibilityURL(nino))(
              Some(HttpResponse(200, responseJson = Some(Json.toJson(
                EligibilityCheckResponse(2, ineligibilityReason))))))

            val result = connector.getEligibility(nino)
            await(result.value) shouldBe Right(
              EligibilityCheckResult(Left(
                IneligibilityReason.fromInt(ineligibilityReason).getOrElse(sys.error(s"Could not get ineligibility reason for $ineligibilityReason"))
              )))
          }
        }

      "return an error" when {
        "the call comes back with a 200 status with a positive result " +
          "and an invalid reason" in {
            forAll { eligibilityReason: Int ⇒
              whenever(!(6 to 8).contains(eligibilityReason)) {
                mockHttpGet(eligibilityURL(nino))(
                  Some(HttpResponse(200, responseJson = Some(Json.toJson(
                    EligibilityCheckResponse(1, eligibilityReason))))))

                val result = connector.getEligibility(nino)
                await(result.value).isLeft shouldBe true
              }
            }
          }

        "the call comes back with a 200 status with a negative result " +
          "and an invalid reason" in {
            forAll { ineligibilityReason: Int ⇒
              whenever(!(1 to 5).contains(ineligibilityReason)) {
                mockHttpGet(eligibilityURL(nino))(
                  Some(HttpResponse(200, responseJson = Some(Json.toJson(
                    EligibilityCheckResponse(2, ineligibilityReason))))))

                val result = connector.getEligibility(nino)
                await(result.value).isLeft shouldBe true
              }
            }
          }

        "the call comes back with a 200 and an unknown result" in {
          forAll { (result: Int, reason: Int) ⇒
            whenever(!(1 to 2).contains(result)) {
              mockHttpGet(eligibilityURL(nino))(
                Some(HttpResponse(200, responseJson = Some(Json.toJson(
                  EligibilityCheckResponse(result, reason))))))

              val r = connector.getEligibility(nino)
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
        mockHttpGet(enrolmentStatusURL(nino)),
        () ⇒ connector.getUserEnrolmentStatus(nino),
        EnrolmentStatus.NotEnrolled
      )

      "return a Right if the call comes back with HTTP status 200 with " +
        "valid JSON in the body" in {
          mockHttpGet(enrolmentStatusURL(nino))(Some(HttpResponse(
            200,
            Some(Json.parse(
              """
              |{
              |  "enrolled"    : true,
              |  "itmpHtSFlag" : true
              |}
            """.stripMargin))
          )))

          await(connector.getUserEnrolmentStatus(nino).value) shouldBe Right(
            EnrolmentStatus.Enrolled(itmpHtSFlag = true))

          mockHttpGet(enrolmentStatusURL(nino))(Some(HttpResponse(
            200,
            Some(Json.parse(
              """
              |{
              |  "enrolled"    : true,
              |  "itmpHtSFlag" : false
              |}
            """.stripMargin))
          )))

          await(connector.getUserEnrolmentStatus(nino).value) shouldBe Right(
            EnrolmentStatus.Enrolled(itmpHtSFlag = false))

          mockHttpGet(enrolmentStatusURL(nino))(Some(HttpResponse(
            200,
            Some(Json.parse(
              """
              |{
              |  "enrolled" : false,
              |  "itmpHtSFlag" : false
              |}
            """.stripMargin))
          )))

          await(connector.getUserEnrolmentStatus(nino).value) shouldBe Right(EnrolmentStatus.NotEnrolled)
        }

    }

    "enrolling a user" must {

      behave like testCommon(
        mockHttpGet(enrolUserURL(nino)),
        () ⇒ connector.enrolUser(nino),
        (),
        testInvalidJSON = false
      )

      "return a Right if the call comes back with HTTP status 200 with " +
        "valid JSON in the body" in {
          mockHttpGet(enrolUserURL(nino))(Some(HttpResponse(200)))

          await(connector.enrolUser(nino).value) shouldBe Right(())
        }

    }

    "setting the ITMP flag" must {

      behave like testCommon(
        mockHttpGet(setITMPFlagURL(nino)),
        () ⇒ connector.setITMPFlag(nino),
        (),
        testInvalidJSON = false
      )

      "return a Right if the call comes back with HTTP status 200 with " +
        "valid JSON in the body" in {
          mockHttpGet(setITMPFlagURL(nino))(Some(HttpResponse(200)))

          await(connector.setITMPFlag(nino).value) shouldBe Right(())
        }
    }

    "storing emails" must {

      val email = "email"

      val encodedEmail = new String(Base64.getEncoder.encode(email.getBytes()))
      behave like testCommon(
        mockHttpGet(storeEmailURL(encodedEmail, nino)),
        () ⇒ connector.storeEmail(email, nino),
        (),
        testInvalidJSON = false
      )

      "return a Right if the call comes back with HTTP status 200 with " +
        "valid JSON in the body" in {
          mockHttpGet(storeEmailURL(encodedEmail, nino))(Some(HttpResponse(200)))

          await(connector.storeEmail(email, nino).value) shouldBe Right(())
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
