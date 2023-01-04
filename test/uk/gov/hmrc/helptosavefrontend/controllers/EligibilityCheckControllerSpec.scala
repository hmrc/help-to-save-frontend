/*
 * Copyright 2023 HM Revenue & Customs
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

import java.time.LocalDate

import cats.data.EitherT
import cats.instances.future._
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.Configuration
import play.api.http.Status
import play.api.mvc.{Result => PlayResult}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.retrieve.{ItmpAddress, ItmpName, Name, ~}
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithCL200
import uk.gov.hmrc.helptosavefrontend.models.TestData.Eligibility._
import uk.gov.hmrc.helptosavefrontend.models.TestData.UserData.validUserInfo
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityCheckResultType.{AlreadyHasAccount, Ineligible}
import uk.gov.hmrc.helptosavefrontend.models.eligibility.{EligibilityCheckResponse, EligibilityCheckResult, EligibilityCheckResultType}
import uk.gov.hmrc.helptosavefrontend.views.html.register.{missing_user_info, not_eligible, think_you_are_eligible, you_are_eligible}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class EligibilityCheckControllerSpec
    extends ControllerSpecWithGuiceApp with CSRFSupport with AuthSupport with EnrolmentAndEligibilityCheckBehaviour
    with SessionStoreBehaviourSupport with ScalaCheckDrivenPropertyChecks {

  def newController(earlyCapCheck: Boolean): EligibilityCheckController = {

    implicit lazy val appConfig: FrontendAppConfig =
      buildFakeApplication(Configuration("enable-early-cap-check" -> earlyCapCheck)).injector
        .instanceOf[FrontendAppConfig]

    new EligibilityCheckController(
      mockHelpToSaveService,
      mockSessionStore,
      mockAuthConnector,
      mockMetrics,
      testCpd,
      testMcc,
      testErrorHandler,
      testMaintenanceSchedule,
      injector.instanceOf[not_eligible],
      injector.instanceOf[you_are_eligible],
      injector.instanceOf[missing_user_info],
      injector.instanceOf[think_you_are_eligible]
    )
  }

  lazy val controller = newController(false)

  private val fakeRequest = FakeRequest("GET", "/")

  def mockEligibilityResult()(result: Either[String, EligibilityCheckResultType]): Unit =
    (mockHelpToSaveService
      .checkEligibility()(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *)
      .returning(EitherT.fromEither[Future](result))

  def mockAccountCreationAllowed(result: Either[String, UserCapResponse]): Unit =
    (mockHelpToSaveService
      .isAccountCreationAllowed()(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *)
      .returning(EitherT.fromEither[Future](result))

  "The EligibilityCheckController" when {

    "displaying the you are eligible page" must {

      def getIsEligible(): Future[PlayResult] = csrfAddToken(controller.getIsEligible)(fakeRequest)

      behave like commonEnrolmentAndSessionBehaviour(getIsEligible)

      "show the you are eligible page if session data indicates that they are eligible" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(
            Right(
              Some(
                HTSSession(
                  Some(Right(randomEligibleWithUserInfo(validUserInfo.copy(dateOfBirth = LocalDate.of(1980, 12, 31))))),
                  None,
                  None
                )
              )
            )
          )
        }

        val result = getIsEligible()
        status(result) shouldBe OK

        val content = contentAsString(result)
        content should include("You are eligible for a Help to Save account")
        content should include("before you can create an account, start saving and earn bonuses, you will need to:")
        content should include(validUserInfo.forename)
        content should include(validUserInfo.surname)
      }

      "redirect to the you are not eligible page if session data indicates that they are not eligible" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(Right(Some(HTSSession(Some(Left(randomIneligibility())), None, None))))
        }

        val result = getIsEligible()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsNotEligible.url)
      }

      "redirect to check eligibility if the session data indicates they have not done the eligibility checks yet" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(Right(Some(HTSSession(None, None, None))))
        }

        val result = getIsEligible()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility.url)
      }
    }

    "displaying the you are not eligible page" must {

      def getIsNotEligible(): Future[PlayResult] = controller.getIsNotEligible(FakeRequest())

      behave like commonEnrolmentAndSessionBehaviour(getIsNotEligible)

      "show the you are not eligible page if session data indicates that they are not eligible" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(Right(Some(HTSSession(Some(Left(randomIneligibility())), None, None))))
        }

        val result = getIsNotEligible()
        status(result) shouldBe OK
        contentAsString(result) should include("not eligible")
      }

      "redirect to the you are eligible page if session data indicates that they are eligible" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(
            Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, None)))
          )
        }

        val result = getIsNotEligible()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsEligible.url)
      }

      "redirect to check eligibility if the session data indicates they have not done the eligibility checks yet" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(Right(Some(HTSSession(None, None, None))))
        }

        val result = getIsNotEligible()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility.url)
      }

      "show an error if the ineligibility reason cannot be parsed" in {
        val eligibilityCheckResult = randomIneligibility().value.eligibilityCheckResult.copy(reasonCode = 999)
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(
            Right(
              Some(
                HTSSession(
                  Some(
                    Left(
                      randomIneligibility().copy(
                        value = EligibilityCheckResponse(eligibilityCheckResult, randomEligibility().value.threshold)
                      )
                    )
                  ),
                  None,
                  None
                )
              )
            )
          )
        }

        val result = getIsNotEligible()
        checkIsTechnicalErrorPage(result)
      }

      "display the uc threshold amount when it can be obtained from DES via the BE when the user is not eligible due to " +
        "reason: NotEntitledToWTCAndUCInsufficient (code 5)" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(Right(Some(HTSSession(Some(Left(notEntitledToWTCAndUCInsufficient())), None, None))))
        }

        val result = getIsNotEligible()
        status(result) shouldBe OK
        contentAsString(result) should include(
          "This is because your household income - in your last monthly assessment period - was less than £"
        )

      }

      "not display the uc threshold amount when it cannot be obtained from DES when the user is not eligible due to " +
        "reason: NotEntitledToWTCAndUCInsufficient (code 5)" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(
            Right(Some(HTSSession(Some(Left(notEntitledToWTCAndUCInsufficientWithNoThreshold())), None, None)))
          )
        }

        val result = getIsNotEligible()
        status(result) shouldBe OK
        contentAsString(result) should include(
          "This is because your household income in your last monthly assessment period was not enough."
        )

      }

      "display the uc threshold amount when it can be obtained from DES via the BE when the user is not eligible due to " +
        "reason: EntitledToWTCNoTCAndInsufficientUC | NotEntitledToWTCAndNoUC (code 4 | 9)" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(Right(Some(HTSSession(Some(Left(ineligibilityReason4or9())), None, None))))
        }

        val result = getIsNotEligible()
        status(result) shouldBe OK
        contentAsString(result) should include(
          "claiming Universal Credit and your household income - in your last monthly assessment period - was £123.45 or more"
        )

      }

      "not display the uc threshold amount when it cannot be obtained from DES when the user is not eligible due to " +
        "reason: EntitledToWTCNoTCAndInsufficientUC | NotEntitledToWTCAndNoUC (code 4 | 9)" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(Right(Some(HTSSession(Some(Left(ineligibilityReason4or9WithNoThreshold())), None, None))))
        }

        val result = getIsNotEligible()
        status(result) shouldBe OK
        contentAsString(result) should include(
          "claiming Universal Credit and your household income - in your last monthly assessment period - was above a certain amount"
        )

      }
    }

    "displaying the you think you're eligible page" must {

      "redirect to the eligibility check if there is no session data" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithCL200)(Some(nino))
          mockSessionStoreGet(Right(None))
        }

        val result = controller.getThinkYouAreEligiblePage(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility.url)

      }

      "show the you're eligible page if the session data indicates that the user is eligible" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithCL200)(Some(nino))
          mockSessionStoreGet(
            Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, None, None, None)))
          )
        }

        val result = controller.getThinkYouAreEligiblePage(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsEligible.url)
      }

      "show the correct page if the session data indicates that the user is ineligible" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithCL200)(Some(nino))
          mockSessionStoreGet(Right(Some(HTSSession(Some(Left(randomIneligibility())), None, None, None, None))))
        }

        val result = controller.getThinkYouAreEligiblePage(FakeRequest())
        val content = contentAsString(result)

        content should include("If you think")
        content should include("If you still think")
      }

    }

    "checking eligibility" when {

      def doCheckEligibilityRequest(): Future[PlayResult] =
        controller.getCheckEligibility(FakeRequest())

      val alreadyHasAccountResponse =
        AlreadyHasAccount(
          EligibilityCheckResponse(
            EligibilityCheckResult("account already exists", 3, "account already opened", 1),
            Some(123.45)
          )
        )

      "getting enrolment statuses are failing" must afterWord("perform an eligibility check and") {

        "show the you are eligible page if the eligibility check indicates the user is eligible" in {
          val eligibleWithUserInfo = randomEligibleWithUserInfo(validUserInfo)

          inSequence {
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockSessionStoreGet(Right(None))
            mockEnrolmentCheck()(Left("Oh no!"))
            mockEligibilityResult()(Right(eligibleWithUserInfo.eligible))
            mockSessionStorePut(HTSSession(Some(Right(eligibleWithUserInfo)), None, None))(Right(()))
          }

          val result = doCheckEligibilityRequest()
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsEligible.url)
        }

        "show the you are not eligible page if the eligibility check indicates the user is ineligible" in {
          val ineligibilityReason = randomIneligibility()

          inSequence {
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockSessionStoreGet(Right(None))
            mockEnrolmentCheck()(Left("Oh no!"))
            mockEligibilityResult()(Right(ineligibilityReason))
            mockSessionStorePut(HTSSession(Some(Left(ineligibilityReason)), None, None))(Right(()))
          }

          val result = doCheckEligibilityRequest()
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsNotEligible.url)
        }

        "redirect to a previously attempted account page if the session indicates there was" +
          "such an attempt previously and set the ITMP flag if the person already has an account" in {
          inSequence {
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockSessionStoreGet(Right(Some(HTSSession.empty.copy(attemptedAccountHolderPageURL = Some("abc")))))
            mockEnrolmentCheck()(Left("Oh no!"))
            mockEligibilityResult()(Right(alreadyHasAccountResponse))
            mockWriteITMPFlag(Right(()))
          }

          val result = doCheckEligibilityRequest()
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("abc")
        }

        "redirect to a previously attempted account page if the session indicates there was" +
          "such an attempt previously even if setting the ITMP flag fails if the person already has an account" in {
          List(
            () ⇒ mockWriteITMPFlag(Left("")),
            () ⇒ mockWriteITMPFlag(None)
          ).foreach { mockWriteFailure ⇒
            inSequence {
              mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
              mockSessionStoreGet(Right(Some(HTSSession.empty.copy(attemptedAccountHolderPageURL = Some("abc")))))
              mockEnrolmentCheck()(Left("Oh no!"))
              mockEligibilityResult()(Right(alreadyHasAccountResponse))
              mockWriteFailure()
            }

            val result = doCheckEligibilityRequest()
            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some("abc")
          }
        }

        "redirect to the NS&I account homepage by default if there is no session and the person" +
          "is enrolled to HTS if the person already has an account" in {
          inSequence {
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockSessionStoreGet(Right(None))
            mockEnrolmentCheck()(Left("Oh no!"))
            mockEligibilityResult()(Right(alreadyHasAccountResponse))
            mockWriteITMPFlag(Right(()))
          }

          val result = doCheckEligibilityRequest()
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(appConfig.nsiManageAccountUrl)
        }

      }

      "the user is already enrolled" must {

        "redirect to a previously attempted account page if the session indicates there was" +
          "such an attempt previously and set the ITMP flag if the person already has an account" in {
          inSequence {
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockSessionStoreGet(Right(Some(HTSSession.empty.copy(attemptedAccountHolderPageURL = Some("abc")))))
            mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(false)))
            mockWriteITMPFlag(Right(()))
          }

          val result = doCheckEligibilityRequest()
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("abc")
        }

        "redirect to a previously attempted account page if the session indicates there was" +
          "such an attempt previously even if setting the ITMP flag fails if the person already has an account" in {
          List(
            () ⇒ mockWriteITMPFlag(Left("")),
            () ⇒ mockWriteITMPFlag(None)
          ).foreach { mockWriteFailure ⇒
            inSequence {
              mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
              mockSessionStoreGet(Right(Some(HTSSession.empty.copy(attemptedAccountHolderPageURL = Some("abc")))))
              mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(false)))
              mockWriteFailure()
            }

            val result = doCheckEligibilityRequest()
            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some("abc")
          }
        }

        "redirect to the NS&I account homepage by default if there is no session and the person" +
          "is enrolled to HTS if the person already has an account" in {
          inSequence {
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockSessionStoreGet(Right(None))
            mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
          }

          val result = doCheckEligibilityRequest()
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(appConfig.nsiManageAccountUrl)
        }

      }

      "the user is not already enrolled" must {

        "immediately redirect to the you are not eligible page if they have session data " +
          "which indicates they are not eligible" in {
          inSequence {
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockSessionStoreGet(Right(Some(HTSSession(Some(Left(randomIneligibility())), None, None))))
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          }

          val result = doCheckEligibilityRequest()
          status(result) shouldBe Status.SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsNotEligible.url)
        }

        "immediately redirect to the you are eligible page if they have session data " +
          "which indicates they are eligible" in {
          inSequence {
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockSessionStoreGet(
              Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, None)))
            )
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          }
          val result = doCheckEligibilityRequest()
          status(result) shouldBe Status.SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsEligible.url)
        }

        "redirect to NS&I if the eligibility check indicates the user already has an account" in {
          val response = EligibilityCheckResult("account already exists", 3, "account already opened", 1)
          inSequence {
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockSessionStoreGet(Right(None))
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockEligibilityResult()(Right(AlreadyHasAccount(EligibilityCheckResponse(response, Some(123.45)))))
            mockWriteITMPFlag(Right(()))
          }

          val result = doCheckEligibilityRequest()
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(appConfig.nsiManageAccountUrl)
        }

        "redirect to the eligible page if there is no session data and the eligibilty check" +
          "indicates that the person is eligible" in {
          val eligibleWithUserInfo = randomEligibleWithUserInfo(validUserInfo)
          EligibilityCheckResult("eligible", 1, "wtc", 6)
          inSequence {
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockSessionStoreGet(Right(None))
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockEligibilityResult()(Right(eligibleWithUserInfo.eligible))
            mockSessionStorePut(HTSSession(Some(Right(eligibleWithUserInfo)), None, None))(Right(()))
          }

          val responseFuture: Future[PlayResult] = doCheckEligibilityRequest()
          val result = Await.result(responseFuture, 5.seconds)
          status(result) shouldBe Status.SEE_OTHER
          redirectLocation(responseFuture) shouldBe Some(routes.EligibilityCheckController.getIsEligible.url)
        }

        "redirect to the not eligible page if there is no session data and the eligibilty check" +
          "indicates that the person is ineligible" in {
          forAll(ineligibilityGen) { ineligibility: Ineligible ⇒
            inSequence {
              mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
              mockSessionStoreGet(Right(None))
              mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
              mockEligibilityResult()(Right(ineligibility))
              mockSessionStorePut(HTSSession(Some(Left(ineligibility)), None, None))(Right(()))
            }

            val result = doCheckEligibilityRequest()
            status(result) shouldBe Status.SEE_OTHER
            redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsNotEligible.url)
          }

        }

        "report missing user info back to the user if they have no session data" in {
          inSequence {
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsMissingUserInfo)
            mockSessionStoreGet(Right(None))
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          }

          val responseFuture: Future[PlayResult] = doCheckEligibilityRequest()

          val result = Await.result(responseFuture, 5.seconds)
          status(result) shouldBe Status.SEE_OTHER
          redirectLocation(responseFuture) shouldBe Some(routes.EligibilityCheckController.getMissingInfoPage.url)
        }

        "do the eligibility checks when the enable-early-cap-check config is set to true " +
          "and the caps have not been reached" in {
          val controller = newController(true)
          val userCapResponse = new UserCapResponse(false, false, false, false)
          val eligibleWithUserInfo = randomEligibleWithUserInfo(validUserInfo)

          inSequence {
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockSessionStoreGet(Right(None))
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockAccountCreationAllowed(Right(userCapResponse))
            mockEligibilityResult()(Right(eligibleWithUserInfo.eligible))
            mockSessionStorePut(HTSSession(Some(Right(eligibleWithUserInfo)), None, None))(Right(()))
          }

          val result = controller.getCheckEligibility(FakeRequest())
          status(result) shouldBe Status.SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsEligible.url)
        }

        "show the TotalCapReached page when the enable-early-cap-check config is set to true " +
          "and the total cap has been reached" in {
          val controller = newController(true)
          val userCapResponse = new UserCapResponse(true, true, false, false)

          inSequence {
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockSessionStoreGet(Right(None))
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockAccountCreationAllowed(Right(userCapResponse))
          }

          val result = controller.getCheckEligibility(FakeRequest())
          status(result) shouldBe Status.SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.RegisterController.getTotalCapReachedPage.url)
        }

        "show the DailyCapReached page when the enable-early-cap-check config is set to true " +
          "and the total cap has been reached" in {
          val controller = newController(true)
          val userCapResponse = new UserCapResponse(true, false, false, false)

          inSequence {
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockSessionStoreGet(Right(None))
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockAccountCreationAllowed(Right(userCapResponse))
          }

          val result = controller.getCheckEligibility(FakeRequest())
          status(result) shouldBe Status.SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.RegisterController.getDailyCapReachedPage.url)
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
                mockSessionStoreGet(Left(""))
              }
            )
          }

          "the eligibility check call returns with an error" in {
            forAll { checkError: String ⇒
              test(
                inSequence {
                  mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
                  mockSessionStoreGet(Right(None))
                  mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
                  mockEligibilityResult()(Left(checkError))
                }
              )
            }
          }

          "there is an error writing to the session cache" in {
            val eligibleWithUserInfo = randomEligibleWithUserInfo(validUserInfo)
            test(inSequence {
              mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
              mockSessionStoreGet(Right(None))
              mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
              mockEligibilityResult()(Right(eligibleWithUserInfo.eligible))
              mockSessionStorePut(HTSSession(Some(Right(eligibleWithUserInfo)), None, None))(Left("Bang"))
            })
          }

        }
      }
    }

    "handling getMissingInfoPage" must {

      "show the user a page informing them which fields of their user info are missing" in {
        import uk.gov.hmrc.helptosavefrontend.controllers.AuthSupport._

        def missingUserInfoRetrieval(
          name: Option[String],
          surname: Option[String],
          dob: Option[LocalDate],
          address: ItmpAddress
        ) =
          new ~(Some(Name(name, surname)), email) and dob and Some(ItmpName(name, None, surname)) and dob and Some(
            address
          ) and mockedNINORetrieval

        def isAddressInvalid(address: ItmpAddress): Boolean =
          !(address.line1.nonEmpty && address.line2.nonEmpty) || address.postCode.isEmpty
        def isNameInvalid(name: Option[String]): Boolean = name.forall(_.isEmpty)
        def isDobInvalid(dob: Option[LocalDate]) = dob.isEmpty

        case class TestParameters(
          name: Option[String],
          surname: Option[String],
          dob: Option[LocalDate],
          address: ItmpAddress
        )

        val itmpAddresses: List[ItmpAddress] = List(
          ItmpAddress(None, Some(line2), None, None, None, Some(postCode), Some(countryCode), Some(countryCode)),
          ItmpAddress(Some(line1), None, None, None, None, Some(postCode), Some(countryCode), Some(countryCode)),
          ItmpAddress(None, None, None, None, None, Some(postCode), Some(countryCode), Some(countryCode)),
          ItmpAddress(Some(line1), Some(line2), None, None, None, None, Some(countryCode), Some(countryCode)),
          ItmpAddress(Some(line1), Some(line2), None, None, None, Some(postCode), Some(countryCode), Some(countryCode))
        )

        val names: List[Option[String]] = List(Some("name"), None, Some(""))

        val dobs: List[Option[LocalDate]] = List(Some(LocalDate.now()), None)

        val testParams: List[TestParameters] = for {
          name ← names
          surname ← names
          dob ← dobs
          address ← itmpAddresses
        } yield TestParameters(name, surname, dob, address)

        testParams.foreach { params ⇒
          if (isNameInvalid(params.name) || isNameInvalid(params.surname) || isDobInvalid(params.dob) || isAddressInvalid(
                params.address
              )) {
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(
              missingUserInfoRetrieval(params.name, params.surname, params.dob, params.address)
            )

            val result: Future[PlayResult] = controller.getMissingInfoPage(FakeRequest())
            status(result) shouldBe Status.OK

            val html = contentAsString(result)

            html.contains("name</li>") shouldBe isNameInvalid(params.name) || isNameInvalid(params.surname)
            html.contains("date of birth</li>") shouldBe isDobInvalid(params.dob)
            html.contains("address</li>") shouldBe isAddressInvalid(params.address)
          }
        }
      }

      "redirect to check eligbility if they aren't missing any info" in {
        mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)

        val response: Future[PlayResult] = controller.getMissingInfoPage()(FakeRequest())

        val result = Await.result(response, 5.seconds)
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(response) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility.url)
      }

    }

    "handling you-are-eligible-submits" must {

      def doRequest(): Future[PlayResult] = controller.youAreEligibleSubmit(FakeRequest())

      "redirect to the give email page if the user has no email" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(Some("nino"))
          mockSessionStoreGet(
            Right(
              Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo.copy(email = None)))), None, None))
            )
          )
        }

        val result = doRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.EmailController.getGiveEmailPage.url)
      }

      "redirect to the select email page if the user has an email" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(Some("nino"))
          mockSessionStoreGet(
            Right(
              Some(
                HTSSession(
                  Some(Right(randomEligibleWithUserInfo(validUserInfo.copy(email = Some("email"))))),
                  None,
                  None
                )
              )
            )
          )
        }

        val result = doRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.EmailController.getSelectEmailPage.url)
      }

      "redirect to the check eligibility page if the user has no session" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(Some("nino"))
          mockSessionStoreGet(Right(None))
        }

        val result = doRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility.url)
      }

      "redirect to the check eligibility page if the user has no eligiblity check result in their session" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(Some("nino"))
          mockSessionStoreGet(Right(Some(HTSSession(None, None, None))))
        }

        val result = doRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility.url)
      }

      "redirect to the not eligible page if the user is not eligible" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(Some("nino"))
          mockSessionStoreGet(Right(Some(HTSSession(Some(Left(randomIneligibility())), None, None))))
        }

        val result = doRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsNotEligible.url)
      }

    }

  }
}
