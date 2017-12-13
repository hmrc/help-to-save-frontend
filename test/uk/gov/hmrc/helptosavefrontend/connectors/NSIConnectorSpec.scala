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

import cats.instances.int._
import cats.syntax.eq._
import org.scalamock.scalatest.MockFactory
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.Configuration
import play.api.http.Status
import play.api.libs.json.{JsObject, Json, Writes}
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig.{nsiAuthHeaderKey, nsiBasicAuth, nsiCreateAccountUrl}
import uk.gov.hmrc.helptosavefrontend.config.WSHttpProxy
import uk.gov.hmrc.helptosavefrontend.connectors.NSIConnector.{SubmissionFailure, SubmissionSuccess}
import uk.gov.hmrc.helptosavefrontend.models.TestData.UserData.validNSIUserInfo
import uk.gov.hmrc.helptosavefrontend.testutil.MockPagerDuty
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Random

class NSIConnectorSpec extends TestSupport with MockFactory with GeneratorDrivenPropertyChecks with MockPagerDuty {

  lazy val mockHTTPProxy = mock[WSHttpProxy]

  def testNSAndIConnectorImpl = new NSIConnectorImpl(
    fakeApplication.configuration ++ Configuration("feature-toggles.log-account-creation-json.enabled" → Random.nextBoolean()),
    mockMetrics,
    mockPagerDuty) {
    override val httpProxy = mockHTTPProxy
  }

  // put in fake authorization details - these should be removed by the call to create an account
  implicit val hc: HeaderCarrier = HeaderCarrier(authorization = Some(Authorization("auth")))

  def mockPost[I](body: I, url: String)(result: Either[String, HttpResponse]): Unit =
    (mockHTTPProxy.post(_: String, _: I, _: Map[String, String])(_: Writes[I], _: HeaderCarrier, _: ExecutionContext))
      .expects(url, body, Map(nsiAuthHeaderKey → nsiBasicAuth), *, *, *)
      .returning(
        result.fold(
          e ⇒ Future.failed(new Exception(e)),
          r ⇒ Future.successful(r)
        ))

  def mockPut[I](body: I, url: String, needsAuditing: Boolean = true)(result: Either[String, HttpResponse]): Unit =
    (mockHTTPProxy.put(_: String, _: I, _: Boolean, _: Map[String, String])(_: Writes[I], _: HeaderCarrier, _: ExecutionContext))
      .expects(url, body, needsAuditing, Map(nsiAuthHeaderKey → nsiBasicAuth), *, *, *)
      .returning(
        result.fold(
          e ⇒ Future.failed(new Exception(e)),
          r ⇒ Future.successful(r)
        ))

  "the updateEmail method" must {
    "return a Right when the status is OK" in {
      mockPut(validNSIUserInfo, nsiCreateAccountUrl)(Right(HttpResponse(Status.OK)))

      val result = testNSAndIConnectorImpl.updateEmail(validNSIUserInfo)
      Await.result(result.value, 3.seconds) shouldBe Right(())
    }

    "return a Left " when {
      "the status is not OK" in {
        forAll{ status: Int ⇒
          whenever(status =!= Status.OK && status > 0){
            inSequence{
              mockPut(validNSIUserInfo, nsiCreateAccountUrl)(Right(HttpResponse(status)))
              // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
              mockPagerDutyAlert("Received unexpected http status in response to update email")
            }

            val result = testNSAndIConnectorImpl.updateEmail(validNSIUserInfo)
            Await.result(result.value, 3.seconds).isLeft shouldBe true
          }
        }
      }

      "the POST to NS&I fails" in {
        inSequence{
          mockPut(validNSIUserInfo, nsiCreateAccountUrl)(Left("Oh no!"))
          // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
          mockPagerDutyAlert("Failed to make call to update email")
        }

        val result = testNSAndIConnectorImpl.updateEmail(validNSIUserInfo)
        Await.result(result.value, 3.seconds).isLeft shouldBe true
      }
    }

  }

  "the createAccount Method" must {

    "Return a SubmissionSuccess when the status is Created" in {
      mockPost(validNSIUserInfo, nsiCreateAccountUrl)(Right(HttpResponse(Status.CREATED)))
      val result = testNSAndIConnectorImpl.createAccount(validNSIUserInfo)
      Await.result(result, 3.seconds) shouldBe SubmissionSuccess()
    }

    "Return a SubmissionSuccess when the status is CONFLICT" in {
      mockPost(validNSIUserInfo, nsiCreateAccountUrl)(Right(HttpResponse(Status.CONFLICT)))
      val result = testNSAndIConnectorImpl.createAccount(validNSIUserInfo)
      Await.result(result, 3.seconds) shouldBe SubmissionSuccess()
    }

    "return a SubmissionFailure" when {
      "the status is BAD_REQUEST" in {
        val submissionFailure = SubmissionFailure(Some("id"), "message", "detail")
        inSequence {
          mockPost(validNSIUserInfo, nsiCreateAccountUrl)(Right(HttpResponse(Status.BAD_REQUEST,
                                                                             Some(JsObject(Seq("error" → Json.toJson(submissionFailure)))))))
          // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
          mockPagerDutyAlert("Received unexpected http status in response to create account")
        }
        val result = testNSAndIConnectorImpl.createAccount(validNSIUserInfo)
        Await.result(result, 3.seconds) shouldBe submissionFailure
      }

      "the status is INTERNAL_SERVER_ERROR" in {
        val submissionFailure = SubmissionFailure(Some("id"), "message", "detail")
        inSequence {
          mockPost(validNSIUserInfo, nsiCreateAccountUrl)(Right(HttpResponse(Status.INTERNAL_SERVER_ERROR,
                                                                             Some(JsObject(Seq("error" → Json.toJson(submissionFailure)))))))
          // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
          mockPagerDutyAlert("Received unexpected http status in response to create account")
        }
        val result = testNSAndIConnectorImpl.createAccount(validNSIUserInfo)
        Await.result(result, 3.seconds) shouldBe submissionFailure
      }

      "the status is SERVICE_UNAVAILABLE" in {
        val submissionFailure = SubmissionFailure(Some("id"), "message", "detail")
        inSequence {
          mockPost(validNSIUserInfo, nsiCreateAccountUrl)(Right(HttpResponse(Status.SERVICE_UNAVAILABLE,
                                                                             Some(JsObject(Seq("error" → Json.toJson(submissionFailure)))))))
          // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
          mockPagerDutyAlert("Received unexpected http status in response to create account")
        }
        val result = testNSAndIConnectorImpl.createAccount(validNSIUserInfo)
        Await.result(result, 3.seconds) shouldBe submissionFailure
      }

      "the status is anything else" in {
        inSequence{
          mockPost(validNSIUserInfo, nsiCreateAccountUrl)(Right(HttpResponse(Status.BAD_GATEWAY)))
          // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
          mockPagerDutyAlert("Received unexpected http status in response to create account")

        }
        val result = testNSAndIConnectorImpl.createAccount(validNSIUserInfo)
        Await.result(result, 3.seconds) match {
          case SubmissionSuccess()  ⇒ fail()
          case _: SubmissionFailure ⇒ ()
        }
      }

      "the call to createAccount fails" in {
        inSequence {
          mockPost(validNSIUserInfo, nsiCreateAccountUrl)(Left(""))
          // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
          mockPagerDutyAlert("Failed to make call to create account")
        }
        val result = testNSAndIConnectorImpl.createAccount(validNSIUserInfo)
        Await.result(result, 3.seconds) shouldBe a[SubmissionFailure]
      }
    }

    "the health-check Method" must {
      "Return a Right when the status is OK" in {
        mockPut(validNSIUserInfo, nsiCreateAccountUrl, false)(Right(HttpResponse(Status.OK)))
        val result = testNSAndIConnectorImpl.healthCheck(validNSIUserInfo)
        Await.result(result.value, 3.seconds) shouldBe Right(())
      }

      "Return a Left when the status is OK" in {
        forAll{ status: Int ⇒
          whenever(status > 0 && status =!= Status.OK){
            mockPut(validNSIUserInfo, nsiCreateAccountUrl, false)(Right(HttpResponse(status)))
            val result = testNSAndIConnectorImpl.healthCheck(validNSIUserInfo)
            Await.result(result.value, 3.seconds).isLeft shouldBe true
          }
        }
      }
    }
  }
}
