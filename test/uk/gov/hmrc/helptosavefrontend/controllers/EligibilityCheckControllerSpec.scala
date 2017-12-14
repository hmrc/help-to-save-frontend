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

import java.time.LocalDate

import cats.data.EitherT
import cats.instances.future._
import org.scalacheck.Gen
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.Configuration
import play.api.http.Status
import play.api.i18n.MessagesApi
import play.api.mvc.{Result ⇒ PlayResult}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.retrieve.{ItmpAddress, ItmpName, Name, ~}
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.config.{AppConfig, FrontendAppConfig}
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityCheckResult.{AlreadyHasAccount, Eligible, Ineligible}
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithCL200
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.TestData.UserData.validUserInfo
import uk.gov.hmrc.helptosavefrontend.models.TestData.Eligibility._
import uk.gov.hmrc.helptosavefrontend.models.eligibility.{EligibilityCheckResponse, EligibilityCheckResult}
import uk.gov.hmrc.helptosavefrontend.util.NINO
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.smartstub.AutoGen

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class EligibilityCheckControllerSpec
  extends AuthSupport
  with CSRFSupport
  with EnrolmentAndEligibilityCheckBehaviour
  with SessionCacheBehaviour
  with GeneratorDrivenPropertyChecks {

  val mockAuditor = mock[HTSAuditor]

  def newController(earlyCapCheck: Boolean): EligibilityCheckController = new EligibilityCheckController(
    fakeApplication.injector.instanceOf[MessagesApi],
    mockHelpToSaveService,
    mockSessionCacheConnector,
    mockAuditor,
    mockAuthConnector,
    mockMetrics,
    Configuration("enable-early-cap-check" → earlyCapCheck))

  val controller = newController(false)
  val trueEarlyCapController = newController(true)

  val mockAppConfig: AppConfig = mock[AppConfig]

  def mockEligibilityResult()(result: Either[String, EligibilityCheckResult]): Unit =
    (mockHelpToSaveService.checkEligibility()(_: HeaderCarrier))
      .expects(*)
      .returning(EitherT.fromEither[Future](result))

  def mockSendAuditEvent(): Unit =
    (mockAuditor.sendEvent(_: HTSEvent, _: NINO))
      .expects(*, nino)
      .returning(Future.successful(AuditResult.Success))

  def mockAccountCreationAllowed(result: Either[String, UserCapResponse]): Unit =
    (mockHelpToSaveService.isAccountCreationAllowed()(_: HeaderCarrier))
      .expects(*)
      .returning(EitherT.fromEither[Future](result))

  "The EligibilityCheckController" when {

    "displaying the you are eligible page" must {

        def getIsEligible(): Future[PlayResult] = controller.getIsEligible(fakeRequestWithCSRFToken)

      behave like commonEnrolmentAndSessionBehaviour(getIsEligible)

      "show the you are eligible page if session data indicates that they are eligible" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Right(validUserInfo.copy(dateOfBirth = LocalDate.of(1980, 12, 31))), None, None))))
        }

        val result = getIsEligible()
        status(result) shouldBe OK

        val content = contentAsString(result)
        content should include("We&#x27;ve checked the details we hold for you and you&#x27;re eligible for an account")
        content should include("By continuing you are confirming that, to the best of your knowledge, these are your details and they are correct")
        content should include(validUserInfo.forename)
        content should include(validUserInfo.surname)
        content should include("31 December 1980")

      }

      "redirect to the you are not eligible page if session data indicates that they are not eligible" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Left(randomIneligibility()), None, None))))
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
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Left(randomIneligibility()), None, None))))
        }

        val result = getIsNotEligible()
        status(result) shouldBe OK
        contentAsString(result) should include("not eligible")
      }

      "redirect to the you are eligible page if session data indicates that they are eligible" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Right(validUserInfo), None, None))))
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
            redirectLocation(result) shouldBe Some(FrontendAppConfig.nsiManageAccountUrl)
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
                redirectLocation(result) shouldBe Some(FrontendAppConfig.nsiManageAccountUrl)
              }
          }

        "show the you are eligible page if the eligibility check indicates the user is eligible" in {
          inSequence {
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockEnrolmentCheck()(Left("Oh no!"))
            mockEligibilityResult()(Right(randomEligibility()))
            mockSessionCacheConnectorPut(HTSSession(Right(validUserInfo), None, None))(Right(()))
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
            mockSessionCacheConnectorPut(HTSSession(Left(ineligibilityReason), None, None))(Right(()))
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
              mockSessionCacheConnectorGet(Right(Some(HTSSession(Left(randomIneligibility()), None, None))))
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
              mockSessionCacheConnectorGet(Right(Some(HTSSession(Right(validUserInfo), None, None))))
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
          redirectLocation(result) shouldBe Some(FrontendAppConfig.nsiManageAccountUrl)
        }

        "return user details if the user is eligible for help-to-save and the " +
          "user is not already enrolled and they have no session data" in {
            val response = EligibilityCheckResponse("eligible", 1, "wtc", 6)
            inSequence {
              mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
              mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
              mockSessionCacheConnectorGet(Right(None))
              mockEligibilityResult()(Right(Eligible(response)))
              mockSessionCacheConnectorPut(HTSSession(Right(validUserInfo), None, None))(Right(()))
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
                mockSessionCacheConnectorPut(HTSSession(Left(ineligibility), None, None))(Right(()))
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
          status(result) shouldBe Status.SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getMissingInfoPage().url)
        }

        "do the eligibility checks when the enable-early-cap-check config is set to true " +
          "and the caps have not been reached" in {
            val userCapResponse = new UserCapResponse(false, false, false, false)

            inSequence {
              mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
              mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
              mockAccountCreationAllowed(Right(userCapResponse))
              mockSessionCacheConnectorGet(Right(Some(HTSSession(Right(validUserInfo), None, None))))
            }

            val result = trueEarlyCapController.getCheckEligibility(FakeRequest())
            status(result) shouldBe Status.SEE_OTHER
            redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsEligible().url)
          }

        "show the TotalCapReached page when the enable-early-cap-check config is set to true " +
          "and the total cap has been reached" in {
            val userCapResponse = new UserCapResponse(true, true, false, false)

            inSequence {
              mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
              mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
              mockAccountCreationAllowed(Right(userCapResponse))
            }

            val result = trueEarlyCapController.getCheckEligibility(FakeRequest())
            status(result) shouldBe Status.SEE_OTHER
            redirectLocation(result) shouldBe Some(routes.RegisterController.getTotalCapReachedPage().url)
          }

        "show the DailyCapReached page when the enable-early-cap-check config is set to true " +
          "and the total cap has been reached" in {
            val userCapResponse = new UserCapResponse(true, false, false, false)

            inSequence {
              mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
              mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
              mockAccountCreationAllowed(Right(userCapResponse))
            }

            val result = trueEarlyCapController.getCheckEligibility(FakeRequest())
            status(result) shouldBe Status.SEE_OTHER
            redirectLocation(result) shouldBe Some(routes.RegisterController.getDailyCapReachedPage().url)
          }

        "show the IsEligible page when the enable-early-cap-check config is set to false " +
          "and neither cap has been reached" in {
            val userCapResponse = new UserCapResponse(false, false, false, false)

            inSequence {
              mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
              mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
              mockSessionCacheConnectorGet(Right(Some(HTSSession(Right(validUserInfo), None, None))))
            }

            val result = controller.getCheckEligibility(FakeRequest())
            status(result) shouldBe Status.SEE_OTHER
            redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsEligible().url)
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
              mockSessionCacheConnectorPut(HTSSession(Right(validUserInfo), None, None))(Left("Bang"))
            })
          }

        }
      }
    }

    "handling getMissingInfoPage" must {

      "show the user a page informing them which fields of their user info are missing" in {
        import uk.gov.hmrc.helptosavefrontend.controllers.AuthSupport._

          def missingUserInfoRetrieval(name:    Option[String],
                                       surname: Option[String],
                                       dob:     Option[org.joda.time.LocalDate],
                                       address: ItmpAddress) =
            new ~(Name(name, surname), email) and dob and ItmpName(name, None, surname) and dob and address and mockedNINORetrieval

          def isAddressInvalid(address: ItmpAddress): Boolean = !(address.line1.nonEmpty && address.line2.nonEmpty) || address.postCode.isEmpty
          def isNameInvalid(name: Option[String]): Boolean = name.forall(_.isEmpty)
          def isDobInvalid(dob: Option[org.joda.time.LocalDate]) = dob.isEmpty

        case class TestParameters(name: Option[String], surname: Option[String], dob: Option[org.joda.time.LocalDate], address: ItmpAddress)

        val itmpAddresses: List[ItmpAddress] = List(
          ItmpAddress(None, Some(line2), None, None, None, Some(postCode), Some(countryCode), Some(countryCode)),
          ItmpAddress(Some(line1), None, None, None, None, Some(postCode), Some(countryCode), Some(countryCode)),
          ItmpAddress(None, None, None, None, None, Some(postCode), Some(countryCode), Some(countryCode)),
          ItmpAddress(Some(line1), Some(line2), None, None, None, None, Some(countryCode), Some(countryCode)),
          ItmpAddress(Some(line1), Some(line2), None, None, None, Some(postCode), Some(countryCode), Some(countryCode))
        )

        val names: List[Option[String]] = List(Some("name"), None, Some(""))

        val dobs: List[Option[org.joda.time.LocalDate]] = List(Some(org.joda.time.LocalDate.now()), None)

        val testParams: List[TestParameters] = for {
          name ← names
          surname ← names
          dob ← dobs
          address ← itmpAddresses
        } yield TestParameters(name, surname, dob, address)

        testParams.foreach { params ⇒
          if (isNameInvalid(params.name) || isNameInvalid(params.surname) || isDobInvalid(params.dob) || isAddressInvalid(params.address)) {
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(missingUserInfoRetrieval(params.name, params.surname, params.dob, params.address))

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
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility().url)
      }

    }

    "handling you-are-eligible-submits" must {

        def doRequest(): Future[PlayResult] = controller.youAreEligibleSubmit(FakeRequest())

      "redirect to the give email page if the user has no email" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(Some("nino"))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Right(validUserInfo.copy(email = None)), None, None))))
        }

        val result = doRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.RegisterController.getGiveEmailPage().url)
      }

      "redirect to the select email page if the user has an email" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(Some("nino"))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Right(validUserInfo.copy(email = Some("email"))), None, None))))
        }

        val result = doRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.RegisterController.getSelectEmailPage().url)
      }

      "redirect to the check eligibility page if the user has no session" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(Some("nino"))
          mockSessionCacheConnectorGet(Right(None))
        }

        val result = doRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility().url)
      }

      "redirect to the not eligible page if the user is not eligible" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(Some("nino"))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Left(randomIneligibility()), None, None))))
        }

        val result = doRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsNotEligible().url)
      }

    }

  }
}
