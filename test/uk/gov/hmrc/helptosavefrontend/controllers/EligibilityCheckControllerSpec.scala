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

import akka.util.Timeout
import cats.data.EitherT
import cats.instances.future._
import cats.syntax.either._
import org.scalatest.BeforeAndAfterAll
import play.api.http.Status
import play.api.i18n.MessagesApi
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.mvc.{Result ⇒ PlayResult}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.connectors.SessionCacheConnector
import uk.gov.hmrc.helptosavefrontend.enrolment.EnrolmentStore
import uk.gov.hmrc.helptosavefrontend.models.EligibilityCheckError.{BackendError, MissingUserInfos}
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithConfidence
import uk.gov.hmrc.helptosavefrontend.models.MissingUserInfo.{Contact, Email}
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.services.{EnrolmentService, HelpToSaveService, JSONSchemaValidationService}
import uk.gov.hmrc.helptosavefrontend.util.{HTSAuditor, NINO, UserDetailsURI}
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

class EligibilityCheckControllerSpec extends TestSupport {

  val nino = "WM123456C"
  val userDetailsURI = "user-details-uri"

  val enrolment = Enrolment("HMRC-NI", Seq(EnrolmentIdentifier("NINO", nino)), "activated", ConfidenceLevel.L200)
  val enrolments = Enrolments(Set(enrolment))
  val userDetailsURIWithEnrolments = core.~[Option[UserDetailsURI],Enrolments](Some(userDetailsURI), enrolments)

  val mockHtsService = mock[HelpToSaveService]
  val mockAuthConnector = mock[PlayAuthConnector]
  val mockSessionCacheConnector: SessionCacheConnector = mock[SessionCacheConnector]
  val jsonSchemaValidationService = mock[JSONSchemaValidationService]
  val mockAuditor = mock[HTSAuditor]
  val mockEnrolmentService = mock[EnrolmentService]


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

  def mockSessionCacheConnectorPut(result: Either[String, CacheMap]): Unit =
    (mockSessionCacheConnector.put(_: HTSSession)(_: Writes[HTSSession], _: HeaderCarrier))
      .expects(*, *, *)
      .returning(result.fold(
        e ⇒ Future.failed(new Exception(e)),
        Future.successful))


  def mockPlayAuthWithWithConfidence(): Unit =
    (mockAuthConnector.authorise(_: Predicate, _: Retrieval[Unit])(_: HeaderCarrier))
      .expects(AuthWithConfidence, *, *)
      .returning(Future.successful(()))

  def mockPlayAuthWithRetrievals[A, B](predicate: Predicate)(result: Option[UserDetailsURI]~Enrolments): Unit =
    (mockAuthConnector.authorise(_: Predicate, _: Retrieval[Option[UserDetailsURI] ~ Enrolments])(_: HeaderCarrier))
      .expects(predicate, HtsAuth.UserDetailsUrlWithAllEnrolments, *)
      .returning(Future.successful(result))

  def mockJsonSchemaValidation(input: NSIUserInfo)(result: Either[String, NSIUserInfo]): Unit =
    (jsonSchemaValidationService.validate(_: JsValue))
      .expects(Json.toJson(input))
      .returning(result.map(Json.toJson(_)))

  def mockEnrolmentCheck(input: NINO)(result: Either[String,EnrolmentStore.Status]): Unit =
    (mockEnrolmentService.getUserEnrolmentStatus(_: NINO)(_: ExecutionContext))
      .expects(input, *)
      .returning(EitherT.fromEither[Future](result))

  def mockWriteITMPFlag(nino: NINO)(result: Either[String,Unit]): Unit =
    (mockEnrolmentService.setITMPFlag(_: NINO)(_: HeaderCarrier, _: ExecutionContext))
    .expects(nino, *, *)
    .returning(EitherT.fromEither[Future](result))


  "The EligiblityCheckController" when {

    "checking eligibility" when {

      def doConfirmDetailsRequest(): Future[PlayResult] =
        controller.confirmDetails(FakeRequest())


      "an error occurs while trying to see if the user is already enrolled" must {

        "return an error" in {
          inSequence{
            mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
            mockEnrolmentCheck(nino)(Left("Oh no!"))
          }
          val result = doConfirmDetailsRequest()

          status(result) shouldBe INTERNAL_SERVER_ERROR
        }

      }


      "the user is already enrolled" must {

        "redirect to NS&I and set the ITMP flag if it has not already been set" in {
          inSequence{
            mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
            mockEnrolmentCheck(nino)(Right(EnrolmentStore.Enrolled(itmpHtSFlag = false)))
            mockWriteITMPFlag(nino)(Right(()))
          }

          val result = doConfirmDetailsRequest()
          status(result) shouldBe OK
        }

        "redirect to NS&I and even if there is an error setting the ITMP flag" in {
          inSequence{
            mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
            mockEnrolmentCheck(nino)(Right(EnrolmentStore.Enrolled(itmpHtSFlag = false)))
            mockWriteITMPFlag(nino)(Left("Uh oh"))
          }

          val result = doConfirmDetailsRequest()
          status(result) shouldBe OK
        }

        "redirect to NS&I and not set the ITMP flag if it has already been set" in {
          inSequence{
            mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
            mockEnrolmentCheck(nino)(Right(EnrolmentStore.Enrolled(itmpHtSFlag = true)))
          }

          val result = doConfirmDetailsRequest()
          status(result) shouldBe OK
        }

      }

      "the user is not already enrolled" must {


        "return user details if the user is eligible for help-to-save and the " +
          "user is not already enrolled" in {
          val user = validUserInfo

          inSequence {
            mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
            mockEnrolmentCheck(nino)(Right(EnrolmentStore.NotEnrolled))
            mockEligibilityResult(nino, userDetailsURI)(Right(EligibilityCheckResult(Some(user))))
            mockJsonSchemaValidation(validNSIUserInfo)(Right(validNSIUserInfo))
            mockSessionCacheConnectorPut(Right(CacheMap("1", Map.empty[String, JsValue])))
            mockSendAuditEvent
          }

          val responseFuture: Future[PlayResult] = doConfirmDetailsRequest()
          val result = Await.result(responseFuture, 5.seconds)

          status(result) shouldBe Status.OK

          contentType(result) shouldBe Some("text/html")
          charset(result) shouldBe Some("utf-8")

          val html = contentAsString(result)

          html should include(user.forename)
          html should include(user.email)
          html should include(user.nino)
          html should include("Sign out")
        }

        "display a 'Not Eligible' page if the user is not eligible and not already enrolled" in {
          inSequence {
            mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
            mockEnrolmentCheck(nino)(Right(EnrolmentStore.NotEnrolled))
            mockEligibilityResult(nino, userDetailsURI)(Right(EligibilityCheckResult(None)))
            mockSendAuditEvent
          }

          val result = doConfirmDetailsRequest()
          status(result) shouldBe Status.SEE_OTHER

          redirectLocation(result) shouldBe Some("/help-to-save/register/not-eligible")
        }

        "report missing user info back to the user" in {
          inSequence {
            mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
            mockEnrolmentCheck(nino)(Right(EnrolmentStore.NotEnrolled))
            mockEligibilityResult(nino, userDetailsURI)(Left(MissingUserInfos(Set(Email, Contact), nino)))
            mockSendAuditEvent
          }

          val responseFuture: Future[PlayResult] = doConfirmDetailsRequest()

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
            val result = doConfirmDetailsRequest()
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

          "the eligibility check call returns with an error" in {
            test(
              inSequence {
                mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
                mockEnrolmentCheck(nino)(Right(EnrolmentStore.NotEnrolled))
                mockEligibilityResult(nino, userDetailsURI)(Left(BackendError("Oh no!", nino)))
              }
            )
          }

          "if the JSON schema validation is unsuccessful" in {
            test(inSequence {
              mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
              mockEnrolmentCheck(nino)(Right(EnrolmentStore.NotEnrolled))
              mockEligibilityResult(nino, userDetailsURI)(Right(EligibilityCheckResult(Some(validUserInfo))))
              mockJsonSchemaValidation(validNSIUserInfo)(Left("uh oh"))
            }
            )
          }

          "there is an error writing to the session cache" in {
            test(inSequence {
              mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
              mockEnrolmentCheck(nino)(Right(EnrolmentStore.NotEnrolled))
              mockEligibilityResult(nino, userDetailsURI)(Right(EligibilityCheckResult(Some(validUserInfo))))
              mockJsonSchemaValidation(validNSIUserInfo)(Right(validNSIUserInfo))
              mockSessionCacheConnectorPut(Left("Bang"))
            }
            )
          }
        }
      }
    }
  }
}
