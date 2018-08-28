/*
 * Copyright 2018 HM Revenue & Customs
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
import play.api.Configuration
import play.api.http.Status
import play.api.mvc.{Result ⇒ PlayResult}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.forms.BankDetails
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.{AuthProvider, AuthWithCL200}
import uk.gov.hmrc.helptosavefrontend.models.TestData.Eligibility._
import uk.gov.hmrc.helptosavefrontend.models.TestData.UserData.{validNSIPayload, validUserInfo}
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.account.AccountNumber
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityCheckResult.Eligible
import uk.gov.hmrc.helptosavefrontend.models.register.CreateAccountRequest
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveServiceImpl.{SubmissionFailure, SubmissionSuccess}
import uk.gov.hmrc.helptosavefrontend.util.Crypto
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class RegisterControllerSpec
  extends AuthSupport
  with CSRFSupport
  with EnrolmentAndEligibilityCheckBehaviour
  with SessionCacheBehaviourSupport
  with GeneratorDrivenPropertyChecks {

  def newController(earlyCapCheck: Boolean)(implicit crypto: Crypto): RegisterController = {

    implicit lazy val appConfig: FrontendAppConfig =
      buildFakeApplication(Configuration("enable-early-cap-check" -> earlyCapCheck)).injector.instanceOf[FrontendAppConfig]

    new RegisterController(
      mockHelpToSaveService,
      mockSessionCacheConnector,
      mockAuthConnector,
      mockMetrics,
      fakeApplication)
  }

  lazy val controller: RegisterController = newController(earlyCapCheck = false)(crypto)

  def mockCreateAccount(createAccountRequest: CreateAccountRequest)(response: Either[SubmissionFailure, SubmissionSuccess] = Right(SubmissionSuccess(Some(AccountNumber("1234567890123"))))): Unit =
    (mockHelpToSaveService.createAccount(_: CreateAccountRequest)(_: HeaderCarrier, _: ExecutionContext))
      .expects(createAccountRequest, *, *)
      .returning(EitherT.fromEither[Future](response))

  def mockEmailUpdate(email: String)(result: Either[String, Unit]): Unit =
    (mockHelpToSaveService.storeConfirmedEmail(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(email, *, *)
      .returning(EitherT.fromEither[Future](result))

  def mockAccountCreationAllowed(result: Either[String, UserCapResponse]): Unit =
    (mockHelpToSaveService.isAccountCreationAllowed()(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *)
      .returning(EitherT.fromEither[Future](result))

  def mockDecrypt(expected: String)(result: Option[String]) =
    (crypto.decrypt(_: String))
      .expects(expected)
      .returning(result.fold[Try[String]](Failure(new Exception))(Success.apply))

  def checkRedirectIfNoEmailInSession(doRequest: ⇒ Future[PlayResult]): Unit = {
    "redirect to the give email page if the session data does not contain an email for the user" in {
      inSequence {
        mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
        mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, None))))
      }

      val result = doRequest
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.EmailController.getSelectEmailPage().url)
    }
  }

  "The RegisterController" when {

    "handling getDailyCapReachedPage" must {

      "return the daily cap reached page" in {
        mockAuthWithNoRetrievals(AuthProvider)

        val result = controller.getDailyCapReachedPage(FakeRequest())
        status(result) shouldBe Status.OK
        contentAsString(result) should include("We have a limit on the number of people who can open an account each day")
      }

    }

    "handling getTotalCapReachedPage" must {

      "return the total cap reached page" in {
        mockAuthWithNoRetrievals(AuthProvider)

        val result = controller.getTotalCapReachedPage(FakeRequest())
        status(result) shouldBe Status.OK
        contentAsString(result) should include("We have a limit on the number of people who can open an account at the moment")
      }

    }

    "handling service_unavailable page" must {

      "return the account create disabled page" in {
        mockAuthWithNoRetrievals(AuthProvider)

        val result = controller.getServiceUnavailablePage(FakeRequest())
        status(result) shouldBe Status.OK
        contentAsString(result) should include("Service unavailable")
      }

    }

    "handling getDetailsAreIncorrect" must {

      "return the details are incorrect page" in {
        mockAuthWithNoRetrievals(AuthProvider)

        val result = controller.getDetailsAreIncorrect(FakeRequest())
        status(result) shouldBe Status.OK
        contentAsString(result) should include("We need your correct details")
      }
    }

    "handling a getCreateAccountPage" must {

      val email = "email"

        def doRequest(): Future[PlayResult] =
          controller.getCreateAccountPage()(fakeRequestWithCSRFToken)

      behave like commonEnrolmentAndSessionBehaviour(() ⇒ doRequest())

      checkRedirectIfNoEmailInSession(doRequest())

      "show the user the create account page if the session data contains a confirmed email" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), Some(email), None))))
        }

        val result = doRequest()
        status(result) shouldBe OK
        contentAsString(result) should include("Accept and create account")
      }

      "show an error page if the eligibility reason cannot be parsed" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo)
            .copy(eligible = Eligible(randomEligibility.value.copy(reasonCode = 999))))), Some(email), None))))
        }

        val result = doRequest()
        checkIsTechnicalErrorPage(result)
      }

    }

    "creating an account" must {
      val confirmedEmail = "confirmed"
      val bankDetails = BankDetails("1", "1", None, "name")
      val userInfo = randomEligibleWithUserInfo(validUserInfo)
      val payload = validNSIPayload.updateEmail(confirmedEmail)
        .copy(nbaDetails = Some(bankDetails))
        .copy(version = Some("V2.0"))
        .copy(systemId = Some("MDTP REGISTRATION"))

      val createAccountRequest = CreateAccountRequest(payload, userInfo.eligible.value.reasonCode)

        def doCreateAccountRequest(): Future[PlayResult] = controller.createAccount(fakeRequestWithCSRFToken)

      behave like commonEnrolmentAndSessionBehaviour(doCreateAccountRequest)

      checkRedirectIfNoEmailInSession(doCreateAccountRequest())

      "retrieve the user info from session cache and indicate to the user that the creation was successful " +
        "and enrol the user if the creation was successful" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(userInfo)), Some(confirmedEmail), None, None, None, Some(bankDetails)))))
            mockCreateAccount(createAccountRequest)()
          }

          val result = doCreateAccountRequest()
          status(result) shouldBe OK
          contentAsString(result) should include("Now your Help to Save account has been created, you can start saving and earning bonuses.")
        }

      "indicate to the user that account creation was successful " +
        "even if the user couldn't be enrolled into hts at this time" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(userInfo)), Some(confirmedEmail), None, None, None, Some(bankDetails)))))
            mockCreateAccount(createAccountRequest)()
          }

          val result = doCreateAccountRequest()
          status(result) shouldBe OK
          contentAsString(result) should include("Now your Help to Save account has been created, you can start saving and earning bonuses.")
        }

      "not update user counts but enrol the user if the user already had an account" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(userInfo)), Some(confirmedEmail), None, None, None, Some(bankDetails)))))
          mockCreateAccount(createAccountRequest)(Right(SubmissionSuccess(None)))
        }

        val result = doCreateAccountRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(appConfig.nsiManageAccountUrl)
      }

      "redirect the user to nsi when they already have an account" in {
        val userInfo = randomEligibleWithUserInfo(validUserInfo)
        val createAccountRequest = CreateAccountRequest(validNSIPayload.updateEmail(confirmedEmail), userInfo.eligible.value.reasonCode)
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(userInfo)), Some(confirmedEmail), None))))
          mockCreateAccount(createAccountRequest)(Right(SubmissionSuccess(None)))
        }

        val result = doCreateAccountRequest()

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(appConfig.nsiManageAccountUrl)
      }

      "redirect the user to the confirm details page if the session indicates they have not done so already" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, None))))
        }

        val result = doCreateAccountRequest()
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.EmailController.getSelectEmailPage().url)
      }

      "redirect user to bank_details page if the session doesn't contain bank details" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(userInfo)), Some(confirmedEmail), None))))
        }

        val result = doCreateAccountRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.BankAccountController.getBankDetailsPage().url)
      }

      "redirect to the create account error page the help to save service returns with an error" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(userInfo)), Some(confirmedEmail), None, None, None, Some(bankDetails)))))
          mockCreateAccount(createAccountRequest)(Left(SubmissionFailure(None, "Uh oh", "Uh oh")))
        }

        val result = doCreateAccountRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.RegisterController.getCreateAccountErrorPage().url)
      }
    }

    "handling getCreateAccountErrorPage" must {
      val confirmedEmail = "confirmed"
        def doRequest(): Future[PlayResult] = controller.getCreateAccountErrorPage(FakeRequest())

      behave like commonEnrolmentAndSessionBehaviour(doRequest)

      "show the error page" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), Some(confirmedEmail), None))))
        }

        val result = doRequest()
        contentAsString(result) should include("We could not create a Help to Save account for you at this time")

      }
    }

    "handling checkYourDetails page" must {

      val bankDetails = BankDetails("123456", "12345678", None, "test user name")

        def doRequest(): Future[PlayResult] = controller.checkDetails()(FakeRequest())

      behave like commonEnrolmentAndSessionBehaviour(() ⇒ doRequest())

      checkRedirectIfNoEmailInSession(doRequest())

      "show the details page for valid users" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), Some("valid@email.com"), None, None, None, Some(bankDetails)))))
        }
        val result = doRequest()

        status(result) shouldBe 200
        contentAsString(result) should include("check your details")
      }

      "show user not eligible page if the user is not eligible" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Left(randomIneligibility())), None, None, None, None, Some(bankDetails)))))
        }
        val result = doRequest()

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsNotEligible().url)
      }

      "handle the case when there are no bank details stored in the session" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), Some("valid@email.com"), None))))
        }
        val result = doRequest()

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.BankAccountController.getBankDetailsPage().url)
      }
    }
  }
}
