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
import cats.syntax.eq._
import org.scalacheck.Arbitrary
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.http.Status
import play.api.i18n.MessagesApi
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Result ⇒ PlayResult}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithCL200
import uk.gov.hmrc.helptosavefrontend.models.IneligibilityReason.AccountAlreadyOpened
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.services.JSONSchemaValidationService
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

      behave like commonEnrolmentAndSessionBehaviour(getIsEligible)

      "show the you are eligible page if session data indicates that they are eligible" in {
        inSequence {
          mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validNSIUserInfo), None))))
        }

        val result = getIsEligible()
        status(result) shouldBe OK

        contentAsString(result) should include("You are eligible")
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

        "redirect to NS&I if the eligibility check indicates the user already has an account" in {
          inSequence {
            mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockEnrolmentCheck()(Left("Oh no!"))
            mockEligibilityResult()(Right(EligibilityCheckResult(Left(IneligibilityReason.AccountAlreadyOpened))))
            mockSessionCacheConnectorPut(HTSSession(None, None))(Right(()))
            mockSendAuditEvent()
            mockWriteITMPFlag()(Right(()))
          }

          val result = doCheckEligibilityRequest()
          status(result) shouldBe OK
          contentAsString(result) shouldBe "You've already got an account - yay!!!"
        }

        "show the you are eligible page if the eligibility check indicates the user is eligible" in {
          inSequence {
            mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockEnrolmentCheck()(Left("Oh no!"))
            mockEligibilityResult()(Right(EligibilityCheckResult(Right(EligibilityReason.WTCWithUC))))
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
            mockEligibilityResult()(Right(EligibilityCheckResult(Left(IneligibilityReason.NotEntitledToWTC(false)))))
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
            status(result) shouldBe INTERNAL_SERVER_ERROR
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
          inSequence {
            mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(None))
            mockEligibilityResult()(Right(EligibilityCheckResult(Left(IneligibilityReason.AccountAlreadyOpened))))
            mockSessionCacheConnectorPut(HTSSession(None, None))(Right(()))
            mockSendAuditEvent()
            mockWriteITMPFlag()(Right(()))
          }

          val result = doCheckEligibilityRequest()
          status(result) shouldBe OK
          contentAsString(result) shouldBe "You've already got an account - yay!!!"
        }

        "return user details if the user is eligible for help-to-save and the " +
          "user is not already enrolled and they have no session data" in {
            val eligibilityReason = randomEligibilityReason()

            inSequence {
              mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
              mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
              mockSessionCacheConnectorGet(Right(None))
              mockEligibilityResult()(Right(EligibilityCheckResult(Right(eligibilityReason))))
              mockJsonSchemaValidation(validNSIUserInfo)(Right(validNSIUserInfo))
              mockSessionCacheConnectorPut(HTSSession(Some(validNSIUserInfo), None))(Right(()))
              mockSendAuditEvent
            }

            val responseFuture: Future[PlayResult] = doCheckEligibilityRequest()
            val result = Await.result(responseFuture, 5.seconds)

            status(result) shouldBe Status.SEE_OTHER
            redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsEligible().url)
          }

        "display a 'Not Eligible' page if the user is not eligible and not already enrolled " +
          "and they have no session data" in {
            implicit val ineligibilityArb: Arbitrary[IneligibilityReason] = Arbitrary(ineligibilityReasonGen)

            forAll { ineligibilityReason: IneligibilityReason ⇒
              whenever(ineligibilityReason =!= AccountAlreadyOpened) {
                inSequence {
                  mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
                  mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
                  mockSessionCacheConnectorGet(Right(None))
                  mockEligibilityResult()(Right(EligibilityCheckResult(Left(ineligibilityReason))))
                  mockSessionCacheConnectorPut(HTSSession(None, None))(Right(()))
                  mockSendAuditEvent
                }

                val result = doCheckEligibilityRequest()
                status(result) shouldBe Status.SEE_OTHER

                redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsNotEligible().url)
              }
            }
          }

        "report missing user info back to the user if they have no session data" in {
          val eligibilityReason = randomEligibilityReason()

          inSequence {
            mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedMissingUserInfo)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(None))
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
              mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedMissingNinoEnrolment)
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
            val eligibilityReason = randomEligibilityReason()

            test(inSequence {
              mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
              mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
              mockSessionCacheConnectorGet(Right(None))
              mockEligibilityResult()(Right(EligibilityCheckResult(Right(eligibilityReason))))
              mockJsonSchemaValidation(validNSIUserInfo)(Left("uh oh"))
            })
          }

          "there is an error writing to the session cache" in {
            val eligibilityReason = randomEligibilityReason()

            test(inSequence {
              mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
              mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
              mockSessionCacheConnectorGet(Right(None))
              mockEligibilityResult()(Right(EligibilityCheckResult(Right(eligibilityReason))))
              mockJsonSchemaValidation(validNSIUserInfo)(Right(validNSIUserInfo))
              mockSessionCacheConnectorPut(HTSSession(Some(validNSIUserInfo), None))(Left("Bang"))
            })
          }
        }
      }
    }
  }
}
