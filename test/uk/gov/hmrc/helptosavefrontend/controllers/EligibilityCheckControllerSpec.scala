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

package uk.gov.hmrc.helptosavefrontend.controllers

import cats.data.EitherT
import cats.instances.future._
import cats.syntax.either._
import play.api.http.Status
import play.api.i18n.MessagesApi
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Result ⇒ PlayResult}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.repo.EnrolmentStore
import uk.gov.hmrc.helptosavefrontend.repo.EnrolmentStore.NotEnrolled
import uk.gov.hmrc.helptosavefrontend.models.EligibilityCheckError.{BackendError, MissingUserInfos}
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithConfidence
import uk.gov.hmrc.helptosavefrontend.models.MissingUserInfo.{Contact, Email}
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.services.{HelpToSaveService, JSONSchemaValidationService}
import uk.gov.hmrc.helptosavefrontend.util.HTSAuditor
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class EligibilityCheckControllerSpec extends TestSupport with EnrolmentAndEligibilityCheckBehaviour{

  val mockHtsService = mock[HelpToSaveService]
  val jsonSchemaValidationService = mock[JSONSchemaValidationService]
  val mockAuditor = mock[HTSAuditor]


  val controller = new EligibilityCheckController(
    fakeApplication.injector.instanceOf[MessagesApi],
    mockHtsService,
    mockSessionCacheConnector,
    jsonSchemaValidationService,
    mockEnrolmentService,
    fakeApplication,
    mockAuditor)(
    ec) {
    override lazy val authConnector: PlayAuthConnector = mockAuthConnector
  }

  def mockEligibilityResult(nino: String, authorisationCode: String)(result: Either[EligibilityCheckError, EligibilityCheckResult]): Unit =
    (mockHtsService.checkEligibility(_: String, _: String)(_: HeaderCarrier))
      .expects(nino, authorisationCode, *)
      .returning(EitherT.fromEither[Future](result))

  def mockSendAuditEvent: Unit =
    (mockAuditor.sendEvent(_: HTSEvent))
      .expects(*)
      .returning(Future.successful(AuditResult.Success))

  def mockJsonSchemaValidation(input: NSIUserInfo)(result: Either[String, NSIUserInfo]): Unit =
    (jsonSchemaValidationService.validate(_: JsValue))
      .expects(Json.toJson(input))
      .returning(result.map(Json.toJson(_)))


  "The EligibilityCheckController" when {
    
    "displaying the you are eligible page" must {

      def getIsEligible(): Future[PlayResult] = controller.getIsEligible(FakeRequest())

      testCommonEnrolmentAndSessionBehaviour(getIsEligible)

      "show the you are eligible page if session data indicates that they are eligible" in {
        inSequence{
          mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
          mockEnrolmentCheck(nino)(Right(NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo)))))
        }

        val result = getIsEligible()
        status(result) shouldBe OK

        contentAsString(result) should include("You are eligible")
      }

      "redirect to the you are not eligible page if session data indicates that they are not eligible" in {
        inSequence{
          mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
          mockEnrolmentCheck(nino)(Right(NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(None))))
        }

        val result = getIsEligible()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.notEligible().url)
      }

    }

    "displaying the you are not eligible page" must {

      def getIsNotEligible(): Future[PlayResult] = controller.notEligible(FakeRequest())

      testCommonEnrolmentAndSessionBehaviour(getIsNotEligible)

      "show the you are not eligible page if session data indicates that they are not eligible" in {
        inSequence{
          mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
          mockEnrolmentCheck(nino)(Right(NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(None))))
        }

        val result = getIsNotEligible()
        status(result) shouldBe OK

        contentAsString(result) should include("not eligible")
      }

      "redirect to the you are eligible page if session data indicates that they are eligible" in {
        inSequence{
          mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
          mockEnrolmentCheck(nino)(Right(NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo)))))
        }

        val result = getIsNotEligible()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsEligible().url)
      }

    }
    

    "checking eligibility" when {

      def doCheckEligibilityRequest(): Future[PlayResult] =
        controller.getCheckEligibility(FakeRequest())

      "checking if the user is already enrolled or if they've already done the eligibility " +
        "check this session" must {
        testCommonEnrolmentAndSessionBehaviour(doCheckEligibilityRequest, testRedirectOnNoSession = false)
      }

      "an error occurs while trying to see if the user is already enrolled" must {

        "return an error" in {
          inSequence{
            mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
            mockEnrolmentCheck(nino)(Left("Oh no!"))
          }
          val result = doCheckEligibilityRequest()

          status(result) shouldBe INTERNAL_SERVER_ERROR
        }

      }

      "the user is not already enrolled" must {


        "immediately redirect to the you are not eligible page if they have session data " +
          "which indicates they are not eligible" in {
          inSequence{
            mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
            mockEnrolmentCheck(nino)(Right(EnrolmentStore.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(None))))
            mockSendAuditEvent
          }

          val result = doCheckEligibilityRequest()

          status(result) shouldBe Status.SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.notEligible().url)
        }

        "immediately redirect to the you are eligible page if they have session data " +
          "which indicates they are eligible" in {
          inSequence{
            mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
            mockEnrolmentCheck(nino)(Right(EnrolmentStore.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo)))))
            mockSendAuditEvent
          }

          val result = doCheckEligibilityRequest()

          status(result) shouldBe Status.SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsEligible().url)
        }


        "return user details if the user is eligible for help-to-save and the " +
          "user is not already enrolled and they have no session data" in {

          inSequence {
            mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
            mockEnrolmentCheck(nino)(Right(EnrolmentStore.NotEnrolled))
            mockSessionCacheConnectorGet(Right(None))
            mockEligibilityResult(nino, userDetailsURI)(Right(EligibilityCheckResult(Some(validUserInfo))))
            mockJsonSchemaValidation(validNSIUserInfo)(Right(validNSIUserInfo))
            mockSessionCacheConnectorPut(HTSSession(Some(validNSIUserInfo)))(Right(CacheMap("1", Map.empty[String, JsValue])))
            mockSendAuditEvent
          }

          val responseFuture: Future[PlayResult] = doCheckEligibilityRequest()
          val result = Await.result(responseFuture, 5.seconds)

          status(result) shouldBe Status.SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsEligible().url)
        }

        "display a 'Not Eligible' page if the user is not eligible and not already enrolled " +
          "and they have no session data" in {
          inSequence {
            mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
            mockEnrolmentCheck(nino)(Right(EnrolmentStore.NotEnrolled))
            mockSessionCacheConnectorGet(Right(None))
            mockEligibilityResult(nino, userDetailsURI)(Right(EligibilityCheckResult(None)))
            mockSessionCacheConnectorPut(HTSSession(None))(Right(CacheMap("1", Map.empty[String, JsValue])))
            mockSendAuditEvent
          }

          val result = doCheckEligibilityRequest()
          status(result) shouldBe Status.SEE_OTHER

          redirectLocation(result) shouldBe Some("/help-to-save/register/not-eligible")
        }

        "report missing user info back to the user if they have no session data" in {
          inSequence {
            mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
            mockEnrolmentCheck(nino)(Right(EnrolmentStore.NotEnrolled))
            mockSessionCacheConnectorGet(Right(None))
            mockEligibilityResult(nino, userDetailsURI)(Left(MissingUserInfos(Set(Email, Contact), nino)))
            mockSendAuditEvent
          }

          val responseFuture: Future[PlayResult] = doCheckEligibilityRequest()

          val result = Await.result(responseFuture, 5.seconds)
          status(result) shouldBe Status.OK

          contentType(result) shouldBe Some("text/html")
          charset(result) shouldBe Some("utf-8")

          val html = contentAsString(result)

          html should include("Email")
          html should include("Contact")
        }

        "return an error" when {

          def isError(result: Future[PlayResult]): Boolean =
            status(result) == 500

          // test if the given mock actions result in an error when `confirm_details` is called
          // on the controller
          def test(mockActions: ⇒ Unit): Unit = {
            mockActions
            val result = doCheckEligibilityRequest()
            isError(result) shouldBe true
          }

          "the nino is not available" in {
            test(
              mockPlayAuthWithRetrievals(AuthWithConfidence)(core.~(Some(userDetailsURI), Enrolments(Set())))
            )
          }

          "the user details URI is not available" in {
            test(
              inSequence{
                mockPlayAuthWithRetrievals(AuthWithConfidence)(core.~(None, enrolments))
                mockEnrolmentCheck(nino)(Right(EnrolmentStore.NotEnrolled))
              }
            )
          }

          "there is an error getting the user's session data" in {
            test(
              inSequence {
                mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
                mockEnrolmentCheck(nino)(Right(EnrolmentStore.NotEnrolled))
                mockSessionCacheConnectorGet(Left(""))
              }
            )
          }

          "the eligibility check call returns with an error" in {
            test(
              inSequence {
                mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
                mockEnrolmentCheck(nino)(Right(EnrolmentStore.NotEnrolled))
                mockSessionCacheConnectorGet(Right(None))
                mockEligibilityResult(nino, userDetailsURI)(Left(BackendError("Oh no!", nino)))
              }
            )
          }



          "if the JSON schema validation is unsuccessful" in {
            test(inSequence {
              mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
              mockEnrolmentCheck(nino)(Right(EnrolmentStore.NotEnrolled))
              mockSessionCacheConnectorGet(Right(None))
              mockEligibilityResult(nino, userDetailsURI)(Right(EligibilityCheckResult(Some(validUserInfo))))
              mockJsonSchemaValidation(validNSIUserInfo)(Left("uh oh"))
            }
            )
          }

          "there is an error writing to the session cache" in {
            test(inSequence {
              mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
              mockEnrolmentCheck(nino)(Right(EnrolmentStore.NotEnrolled))
              mockSessionCacheConnectorGet(Right(None))
              mockEligibilityResult(nino, userDetailsURI)(Right(EligibilityCheckResult(Some(validUserInfo))))
              mockJsonSchemaValidation(validNSIUserInfo)(Right(validNSIUserInfo))
              mockSessionCacheConnectorPut(HTSSession(Some(validNSIUserInfo)))(Left("Bang"))
            }
            )
          }
        }
      }
    }
  }
}
