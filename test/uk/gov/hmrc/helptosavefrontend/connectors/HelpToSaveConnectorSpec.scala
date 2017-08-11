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
import play.api.libs.json.Json
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig.encoded
import uk.gov.hmrc.helptosavefrontend.config.{FrontendAppConfig, WSHttpExtension}
import uk.gov.hmrc.helptosavefrontend.connectors.HelpToSaveConnectorImpl.{EligibilityCheckResponse, MissingUserInfoSet}
import uk.gov.hmrc.helptosavefrontend.models.MissingUserInfo.{Contact, DateOfBirth, Email, GivenName, Surname}
import uk.gov.hmrc.helptosavefrontend.models.UserInformationRetrievalError.MissingUserInfos
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.util.{NINO, UserDetailsURI}
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

// scalastyle:off magic.number
class HelpToSaveConnectorSpec extends TestSupport with GeneratorDrivenPropertyChecks {

  def eligibilityURL(nino: NINO): String =
    s"${FrontendAppConfig.helpToSaveUrl}/help-to-save/eligibility-check?nino=$nino"

  def userInformationURL(nino: NINO, userDetailsURI: UserDetailsURI): String =
    s"${FrontendAppConfig.helpToSaveUrl}/help-to-save/user-information?nino=$nino&userDetailsURI=${encoded(userDetailsURI)}"


  class TestApparatus {
    val mockHttp = mock[WSHttpExtension]

    val connector = new HelpToSaveConnectorImpl {
      override val http = mockHttp
    }

    def mockGetEligibilityStatus[I](url: String)(result: HttpResponse): Unit =
      (mockHttp.get(_: String)(_: HeaderCarrier))
        .expects(url, *)
        .returning(Future.successful(result))
  }

  "The HelpToSaveConnectorImpl" when {

    "getting eligibility status" should {

      val nino = "nino"

      "perform a GET request to the help-to-save-service" in new TestApparatus {
        mockGetEligibilityStatus(eligibilityURL(nino))(HttpResponse(200))
        connector.getEligibility(nino)
      }

      "return an EligibilityResult if the call comes back with a 200 status with a positive result " +
        "and a valid reason" in new TestApparatus {
        (6 to 8).foreach { eligibilityReason ⇒
          mockGetEligibilityStatus(eligibilityURL(nino))(
            HttpResponse(200, responseJson = Some(Json.toJson(EligibilityCheckResponse(1, eligibilityReason)))))

          val result = connector.getEligibility(nino)
          Await.result(result.value, 3.seconds) shouldBe Right(
            EligibilityCheckResult(Right(EligibilityReason.fromInt(eligibilityReason).get)))
        }
      }

      "return an EligibilityResult if the call comes back with a 200 status with a negative result " +
        "and a valid reason" in new TestApparatus {
        (1 to 5).foreach { ineligibilityReason ⇒
          mockGetEligibilityStatus(eligibilityURL(nino))(
            HttpResponse(200, responseJson = Some(Json.toJson(
              EligibilityCheckResponse(2, ineligibilityReason)))))

          val result = connector.getEligibility(nino)
          Await.result(result.value, 3.seconds) shouldBe Right(
            EligibilityCheckResult(Left(IneligibilityReason.fromInt(ineligibilityReason).get)))
        }
      }

      "return an error" when {
        "the call comes back with a 200 status with a positive result " +
          "and an invalid reason" in new TestApparatus {
          forAll { eligibilityReason: Int ⇒
            whenever(!(6 to 8).contains(eligibilityReason)) {
              mockGetEligibilityStatus(eligibilityURL(nino))(
                HttpResponse(200, responseJson = Some(Json.toJson(
                  EligibilityCheckResponse(1, eligibilityReason)))))

              val result = connector.getEligibility(nino)
              Await.result(result.value, 3.seconds).isLeft shouldBe true
            }
          }
        }

        "the call comes back with a 200 status with a negative result " +
          "and an invalid reason" in new TestApparatus {
          forAll { ineligibilityReason: Int ⇒
            whenever(!(1 to 5).contains(ineligibilityReason)) {
              mockGetEligibilityStatus(eligibilityURL(nino))(
                HttpResponse(200, responseJson = Some(Json.toJson(
                  EligibilityCheckResponse(2, ineligibilityReason)))))

              val result = connector.getEligibility(nino)
              Await.result(result.value, 3.seconds).isLeft shouldBe true
            }
          }
        }

        "the call comes back with a 200 and an unknown result" in new TestApparatus {
          forAll { (result: Int, reason: Int) ⇒
            whenever(!(1 to 2).contains(result)) {
              mockGetEligibilityStatus(eligibilityURL(nino))(
                HttpResponse(200, responseJson = Some(Json.toJson(
                  EligibilityCheckResponse(result, reason)))))

              val r = connector.getEligibility(nino)
              Await.result(r.value, 3.seconds).isLeft shouldBe true
            }
          }
        }

        "the call comes back with a 200 and an unknown JSON format" in new TestApparatus {
          mockGetEligibilityStatus(eligibilityURL(nino))(
            HttpResponse(200, responseJson = Some(Json.parse(
              """
                |{
                |  "foo": "bar"
                |}
              """.stripMargin
            ))))

          val r = connector.getEligibility(nino)
          Await.result(r.value, 3.seconds).isLeft shouldBe true
        }

        "the call comes back with any other status other than 200" in new TestApparatus {
          forAll{ status: Int ⇒
            whenever(status != 200) {
              // check we get an error even though therecool was valid JSON in the response
              mockGetEligibilityStatus(eligibilityURL(nino))(
                HttpResponse(status, responseJson = Some(Json.toJson(
                  EligibilityCheckResponse(2, 1)))))

              val r = connector.getEligibility(nino)
              Await.result(r.value, 3.seconds).isLeft shouldBe true

            }
          }
        }
      }
    }

    "getting user information" should {
      val nino = "nino"
      val userDetailsURI = "http://user-details-uri"

      "perform a GET request to the help-to-save-service" in new TestApparatus {
        mockGetEligibilityStatus(userInformationURL(nino, userDetailsURI))(HttpResponse(200))

        val result = connector.getUserInformation(nino, userDetailsURI)
        Await.result(result.value, 3.seconds)

      }

      "return the user info if the call comes back with a 200 " +
        "and the body contains user info" in new TestApparatus{
        val userInfo: UserInfo = randomUserInfo()

        mockGetEligibilityStatus(userInformationURL(nino, userDetailsURI))(
          HttpResponse(200, Some(Json.toJson(userInfo))))

        val result = connector.getUserInformation(nino, userDetailsURI)
        Await.result(result.value, 3.seconds) shouldBe Right(userInfo)
      }

      "return missing user info if the call comes back with a 200 " +
        "and the body contains missing user info" in new TestApparatus{
        val missingInfo: Set[MissingUserInfo] = Set(Surname, GivenName, Email, DateOfBirth, Contact)
        val eligibilityResponse = MissingUserInfoSet(missingInfo)

        mockGetEligibilityStatus(userInformationURL(nino, userDetailsURI))(
          HttpResponse(200, responseJson = Some(Json.toJson(eligibilityResponse))))

        val result = connector.getUserInformation(nino, userDetailsURI)
        Await.result(result.value, 3.seconds) shouldBe Left(MissingUserInfos(missingInfo, nino))
      }

      "return an error" when {

        "the call comes back with a 200 with unknown JSON" in new TestApparatus{
          mockGetEligibilityStatus(userInformationURL(nino, userDetailsURI))(
            HttpResponse(200, Some(Json.parse(
              """
                |{
                |  "x" : 1
                |}
              """.stripMargin))))

          val result = connector.getUserInformation(nino, userDetailsURI)
          Await.result(result.value, 3.seconds).isLeft shouldBe true
        }

        "the call comes back with any other status other than 200" in new TestApparatus{
          forAll{ status: Int ⇒
            whenever(status != 200) {
              val userInfo: UserInfo = randomUserInfo()

              mockGetEligibilityStatus(userInformationURL(nino, userDetailsURI))(
                HttpResponse(status, Some(Json.toJson(userInfo))))

              val result = connector.getUserInformation(nino, userDetailsURI)
              Await.result(result.value, 3.seconds).isLeft shouldBe true

            }
          }
        }
      }
    }
  }
}
