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
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.http.Status
import play.api.i18n.MessagesApi
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Result ⇒ PlayResult}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.models.EligibilityCheckResult.{AlreadyHasAccount, Eligible, Ineligible}
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithCL200
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.TestData.UserData.validNSIUserInfo
import uk.gov.hmrc.helptosavefrontend.models.TestData.Eligibility._
import uk.gov.hmrc.helptosavefrontend.services.JSONSchemaValidationService
import uk.gov.hmrc.helptosavefrontend.util.NINO
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class EligibilityCheckControllerSpec
  extends AuthSupport
  with EnrolmentAndEligibilityCheckBehaviour
  with GeneratorDrivenPropertyChecks {

  val jsonSchemaValidationService = mock[JSONSchemaValidationService]
  val mockAuditor = mock[HTSAuditor]

  lazy val controller = new EligibilityCheckController(
    fakeApplication.injector.instanceOf[MessagesApi],
    mockHelpToSaveService,
    mockSessionCacheConnector,
    jsonSchemaValidationService,
    fakeApplication,
    mockAuditor,
    mockAuthConnector,
    mockMetrics)(ec)

  def mockEligibilityResult()(result: Either[String, EligibilityCheckResult]): Unit =
    (mockHelpToSaveService.checkEligibility()(_: HeaderCarrier))
      .expects(*)
      .returning(EitherT.fromEither[Future](result))

  def mockSendAuditEvent(): Unit =
    (mockAuditor.sendEvent(_: HTSEvent, _: NINO))
      .expects(*, nino)
      .returning(Future.successful(AuditResult.Success))

  def mockJsonSchemaValidation(input: NSIUserInfo)(result: Either[String, NSIUserInfo]): Unit =
    (jsonSchemaValidationService.validate(_: JsValue))
      .expects(Json.toJson(input))
      .returning(result.map(Json.toJson(_)))

  "The EligibilityCheckController" when {

    "displaying the you are eligible page" must {

        def getIsEligible(): Future[PlayResult] = controller.getIsEligible(FakeRequest())

      behave like commonEnrolmentAndSessionBehaviour(getIsEligible)

      "show the you are eligible page if session data indicates that they are eligible" in {
        inSequence {
          mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), None))))
        }

        val result = getIsEligible()
        status(result) shouldBe OK

        val content = contentAsString(result)
        content should include("You are eligible")
        content should include(validNSIUserInfo.forename)
        content should include(validNSIUserInfo.surname)
      }

      "redirect to the you are not eligible page if session data indicates that they are not eligible" in {
        inSequence {
          mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None))))
        }

        val result = getIsEligible()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsNotEligible().url)
      }

    }

    "displaying the you are not eligible page" must {

        def getIsNotEligible(): Future[PlayResult] = controller.getIsNotEligible(FakeRequest())

      behave like commonEnrolmentAndSessionBehaviour(getIsNotEligible)

      "show the you are not eligible page if session data indicates that they are not eligible" in {
        inSequence {
          mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None))))
        }

        val result = getIsNotEligible()
        status(result) shouldBe OK

        contentAsString(result) should include("not eligible")
      }

      "redirect to the you are eligible page if session data indicates that they are eligible" in {
        inSequence {
          mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), None))))
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

          behave like commonEnrolmentAndSessionBehaviour(doCheckEligibilityRequest, testRedirectOnNoSession = false, testEnrolmentCheckError = false)

        }

      "an error occurs while trying to see if the user is already enrolled" must {
        "call the get eligibility endpoint of the help to save service" in {
          inSequence {
            mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockEnrolmentCheck()(Left("Oh no!"))
            mockEligibilityResult()(Left(""))
          }

          await(doCheckEligibilityRequest())
        }

        "redirect to NS&I if the eligibility check indicates the user already has an account " +
          "and update the ITMP flag if necessary" in {
            val response = EligibilityCheckResponse("account already exists", 3, "account already opened", 1)
            inSequence {
              mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
              mockEnrolmentCheck()(Left("Oh no!"))
              mockEligibilityResult()(Right(AlreadyHasAccount(response)))
              mockSessionCacheConnectorPut(HTSSession(None, None))(Right(()))
              mockSendAuditEvent()
              mockWriteITMPFlag(Right(()))
            }

            val result = doCheckEligibilityRequest()
            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some(routes.NSIController.goToNSI().url)
          }

        "redirect to NS&I if the eligibility check indicates the user already has an account" +
          "even if the ITMP flag update is unsuccessful" in {
            val response = EligibilityCheckResponse("account already exists", 3, "account already opened", 1)
            List(
              () ⇒ mockWriteITMPFlag(Left("")),
              () ⇒ mockWriteITMPFlag(None)
            ).foreach{ mockWriteFailure ⇒
                inSequence {
                  mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
                  mockEnrolmentCheck()(Left("Oh no!"))
                  mockEligibilityResult()(Right(AlreadyHasAccount(response)))
                  mockSessionCacheConnectorPut(HTSSession(None, None))(Right(()))
                  mockSendAuditEvent()
                  mockWriteFailure()
                }

                val result = doCheckEligibilityRequest()
                status(result) shouldBe SEE_OTHER
                redirectLocation(result) shouldBe Some(routes.NSIController.goToNSI().url)
              }
          }

        "show the you are eligible page if the eligibility check indicates the user is eligible" in {
          inSequence {
            mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockEnrolmentCheck()(Left("Oh no!"))
            mockEligibilityResult()(Right(randomEligibility()))
            mockJsonSchemaValidation(validNSIUserInfo)(Right(validNSIUserInfo))
            mockSessionCacheConnectorPut(HTSSession(Some(validNSIUserInfo), None))(Right(()))
            mockSendAuditEvent()
          }

          val result = doCheckEligibilityRequest()
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsEligible().url)
        }

        "show the you are not eligible page if the eligibility check indicates the user is eligible" in {
          inSequence {
            mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockEnrolmentCheck()(Left("Oh no!"))
            mockEligibilityResult()(Right(randomIneligibility()))
            mockSessionCacheConnectorPut(HTSSession(None, None))(Right(()))
            mockSendAuditEvent()
          }

          val result = doCheckEligibilityRequest()
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsNotEligible().url)
        }

        "return an error" when {

          "the eligibility check call returns with an error" in {
            inSequence {
              mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
              mockEnrolmentCheck()(Left("Oh no!"))
              mockEligibilityResult()(Left(""))
            }

            val result = doCheckEligibilityRequest()
            checkIsTechnicalErrorPage(result)
          }
        }
      }

      "the user is not already enrolled" must {

        "immediately redirect to the you are not eligible page if they have session data " +
          "which indicates they are not eligible" in {
            inSequence {
              mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
              mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
              mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None))))
            }

            val result = doCheckEligibilityRequest()

            status(result) shouldBe Status.SEE_OTHER
            redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsNotEligible().url)
          }

        "immediately redirect to the you are eligible page if they have session data " +
          "which indicates they are eligible" in {
            inSequence {
              mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
              mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
              mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), None))))
            }
            val result = doCheckEligibilityRequest()

            status(result) shouldBe Status.SEE_OTHER
            redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsEligible().url)
          }

        "redirect to NS&I if the eligibility check indicates the user already has an account" in {
          val response = EligibilityCheckResponse("account already exists", 3, "account already opened", 1)
          inSequence {
            mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(None))
            mockEligibilityResult()(Right(AlreadyHasAccount(response)))
            mockSessionCacheConnectorPut(HTSSession(None, None))(Right(()))
            mockSendAuditEvent()
            mockWriteITMPFlag(Right(()))
          }

          val result = doCheckEligibilityRequest()
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.NSIController.goToNSI().url)
        }

        "return user details if the user is eligible for help-to-save and the " +
          "user is not already enrolled and they have no session data" in {
            val response = EligibilityCheckResponse("eligible", 1, "wtc", 6)
            inSequence {
              mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
              mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
              mockSessionCacheConnectorGet(Right(None))
              mockEligibilityResult()(Right(Eligible(response)))
              mockJsonSchemaValidation(validNSIUserInfo)(Right(validNSIUserInfo))
              mockSessionCacheConnectorPut(HTSSession(Some(validNSIUserInfo), None))(Right(()))
              mockSendAuditEvent()
            }

            val responseFuture: Future[PlayResult] = doCheckEligibilityRequest()
            val result = Await.result(responseFuture, 5.seconds)

            status(result) shouldBe Status.SEE_OTHER
            redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsEligible().url)
          }

        "display a 'Not Eligible' page if the user is not eligible and not already enrolled " +
          "and they have no session data" in {
            forAll(ineligibilityGen) { ineligibility: Ineligible ⇒
              inSequence {
                mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
                mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
                mockSessionCacheConnectorGet(Right(None))
                mockEligibilityResult()(Right(ineligibility))
                mockSessionCacheConnectorPut(HTSSession(None, None))(Right(()))
                mockSendAuditEvent()
              }

              val result = doCheckEligibilityRequest()
              status(result) shouldBe Status.SEE_OTHER

              redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsNotEligible().url)
            }

          }

        "report missing user info back to the user if they have no session data" in {
          inSequence {
            mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrivalsMissingUserInfo)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(None))
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
              mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsMissingNinoEnrolment)
            )
          }

          "there is an error getting the user's session data" in {
            test(
              inSequence {
                mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
                mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
                mockSessionCacheConnectorGet(Left(""))
              }
            )
          }

          "the eligibility check call returns with an error" in {
            forAll { checkError: String ⇒
              test(
                inSequence {
                  mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
                  mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
                  mockSessionCacheConnectorGet(Right(None))
                  mockEligibilityResult()(Left(checkError))
                }
              )
            }
          }

          "if the JSON schema validation is unsuccessful" in {
            test(inSequence {
              mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
              mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
              mockSessionCacheConnectorGet(Right(None))
              mockEligibilityResult()(Right(randomEligibility()))
              mockJsonSchemaValidation(validNSIUserInfo)(Left("uh oh"))
            })
          }

          "there is an error writing to the session cache" in {
            test(inSequence {
              mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
              mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
              mockSessionCacheConnectorGet(Right(None))
              mockEligibilityResult()(Right(randomEligibility()))
              mockJsonSchemaValidation(validNSIUserInfo)(Right(validNSIUserInfo))
              mockSessionCacheConnectorPut(HTSSession(Some(validNSIUserInfo), None))(Left("Bang"))
            })
          }
        }
      }
    }
  }
}
