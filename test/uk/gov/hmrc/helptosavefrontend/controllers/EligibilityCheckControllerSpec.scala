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
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.http.Status
import play.api.i18n.MessagesApi
import play.api.mvc.{Result ⇒ PlayResult}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityCheckResult.{AlreadyHasAccount, Eligible, Ineligible}
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithCL200
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.TestData.UserData.validUserInfo
import uk.gov.hmrc.helptosavefrontend.models.TestData.Eligibility._
import uk.gov.hmrc.helptosavefrontend.models.eligibility.{EligibilityCheckResponse, EligibilityCheckResult}
import uk.gov.hmrc.helptosavefrontend.util.NINO
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class EligibilityCheckControllerSpec
  extends AuthSupport
  with EnrolmentAndEligibilityCheckBehaviour
  with GeneratorDrivenPropertyChecks {

  val mockAuditor = mock[HTSAuditor]

  lazy val controller = new EligibilityCheckController(
    fakeApplication.injector.instanceOf[MessagesApi],
    mockHelpToSaveService,
    mockSessionCacheConnector,
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

  "The EligibilityCheckController" when {

    "displaying the you are eligible page" must {

        def getIsEligible(): Future[PlayResult] = controller.getIsEligible(FakeRequest())

      behave like commonEnrolmentAndSessionBehaviour(getIsEligible)

      "show the you are eligible page if session data indicates that they are eligible" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Right(validUserInfo), None))))
        }

        val result = getIsEligible()
        status(result) shouldBe OK

        val content = contentAsString(result)
        content should include("You are eligible")
        content should include(validUserInfo.forename)
        content should include(validUserInfo.surname)
      }

      "redirect to the you are not eligible page if session data indicates that they are not eligible" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Left(randomIneligibility()), None))))
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
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Left(randomIneligibility()), None))))
        }

        val result = getIsNotEligible()
        status(result) shouldBe OK

        contentAsString(result) should include("not eligible")
      }

      "redirect to the you are eligible page if session data indicates that they are eligible" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Right(validUserInfo), None))))
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

          behave like commonEnrolmentAndSessionBehaviour(
            doCheckEligibilityRequest,
            mockSuccessfulAuth      = () ⇒ mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals),
            mockNoNINOAuth          = () ⇒ mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsMissingNinoEnrolment),
            testRedirectOnNoSession = false,
            testEnrolmentCheckError = false)

        }

      "an error occurs while trying to see if the user is already enrolled" must {
        "call the get eligibility endpoint of the help to save service" in {
          inSequence {
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockEnrolmentCheck()(Left("Oh no!"))
            mockEligibilityResult()(Left(""))
          }

          await(doCheckEligibilityRequest())
        }

        "redirect to NS&I if the eligibility check indicates the user already has an account " +
          "and update the ITMP flag if necessary" in {
            val response = EligibilityCheckResponse("account already exists", 3, "account already opened", 1)
            inSequence {
              mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
              mockEnrolmentCheck()(Left("Oh no!"))
              mockEligibilityResult()(Right(AlreadyHasAccount(response)))
              mockSendAuditEvent()
              mockWriteITMPFlag(Right(()))
            }

            val result = doCheckEligibilityRequest()
            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some(routes.NSIController.goToNSI().url)
          }

        "redirect to NS&I if the eligibility check indicates the user already has an account " +
          "even if the ITMP flag update is unsuccessful" in {
            val response = EligibilityCheckResponse("account already exists", 3, "account already opened", 1)
            List(
              () ⇒ mockWriteITMPFlag(Left("")),
              () ⇒ mockWriteITMPFlag(None)
            ).foreach{ mockWriteFailure ⇒
                inSequence {
                  mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
                  mockEnrolmentCheck()(Left("Oh no!"))
                  mockEligibilityResult()(Right(AlreadyHasAccount(response)))
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
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockEnrolmentCheck()(Left("Oh no!"))
            mockEligibilityResult()(Right(randomEligibility()))
            mockSessionCacheConnectorPut(HTSSession(Right(validUserInfo), None))(Right(()))
            mockSendAuditEvent()
          }

          val result = doCheckEligibilityRequest()
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsEligible().url)
        }

        "show the you are not eligible page if the eligibility check indicates the user is ineligible" in {
          val ineligibilityReason = randomIneligibility()

          inSequence {
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockEnrolmentCheck()(Left("Oh no!"))
            mockEligibilityResult()(Right(ineligibilityReason))
            mockSessionCacheConnectorPut(HTSSession(Left(ineligibilityReason), None))(Right(()))
            mockSendAuditEvent()
          }

          val result = doCheckEligibilityRequest()
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsNotEligible().url)
        }

        "return an error" when {

          "the eligibility check call returns with an error" in {
            inSequence {
              mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
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
              mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
              mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
              mockSessionCacheConnectorGet(Right(Some(HTSSession(Left(randomIneligibility()), None))))
            }

            val result = doCheckEligibilityRequest()

            status(result) shouldBe Status.SEE_OTHER
            redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsNotEligible().url)
          }

        "immediately redirect to the you are eligible page if they have session data " +
          "which indicates they are eligible" in {
            inSequence {
              mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
              mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
              mockSessionCacheConnectorGet(Right(Some(HTSSession(Right(validUserInfo), None))))
            }
            val result = doCheckEligibilityRequest()

            status(result) shouldBe Status.SEE_OTHER
            redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsEligible().url)
          }

        "redirect to NS&I if the eligibility check indicates the user already has an account" in {
          val response = EligibilityCheckResponse("account already exists", 3, "account already opened", 1)
          inSequence {
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(None))
            mockEligibilityResult()(Right(AlreadyHasAccount(response)))
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
              mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
              mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
              mockSessionCacheConnectorGet(Right(None))
              mockEligibilityResult()(Right(Eligible(response)))
              mockSessionCacheConnectorPut(HTSSession(Right(validUserInfo), None))(Right(()))
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
                mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
                mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
                mockSessionCacheConnectorGet(Right(None))
                mockEligibilityResult()(Right(ineligibility))
                mockSessionCacheConnectorPut(HTSSession(Left(ineligibility), None))(Right(()))
                mockSendAuditEvent()
              }

              val result = doCheckEligibilityRequest()
              status(result) shouldBe Status.SEE_OTHER

              redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsNotEligible().url)
            }

          }

        "report missing user info back to the user if they have no session data" in {
          inSequence {
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsMissingUserInfo)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(None))
          }

          val responseFuture: Future[PlayResult] = doCheckEligibilityRequest()

          val result = Await.result(responseFuture, 5.seconds)
          status(result) shouldBe Status.OK

          contentType(result) shouldBe Some("text/html")
          charset(result) shouldBe Some("utf-8")

          val html = contentAsString(result)

          html should include("Name")
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
              mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsMissingNinoEnrolment)
            )
          }

          "there is an error getting the user's session data" in {
            test(
              inSequence {
                mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
                mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
                mockSessionCacheConnectorGet(Left(""))
              }
            )
          }

          "the eligibility check call returns with an error" in {
            forAll { checkError: String ⇒
              test(
                inSequence {
                  mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
                  mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
                  mockSessionCacheConnectorGet(Right(None))
                  mockEligibilityResult()(Left(checkError))
                }
              )
            }
          }

          "there is an error writing to the session cache" in {
            test(inSequence {
              mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
              mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
              mockSessionCacheConnectorGet(Right(None))
              mockEligibilityResult()(Right(randomEligibility()))
              mockSessionCacheConnectorPut(HTSSession(Right(validUserInfo), None))(Left("Bang"))
            })
          }
        }
      }
    }
  }
}
