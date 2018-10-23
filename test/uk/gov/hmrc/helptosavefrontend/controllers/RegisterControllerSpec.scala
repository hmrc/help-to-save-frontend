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

import java.util.UUID

import cats.data.EitherT
import cats.instances.future._
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.Configuration
import play.api.http.Status
import play.api.mvc.{Result ⇒ PlayResult}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.forms.{BankDetails, SortCode}
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.{AuthProvider, AuthWithCL200}
import uk.gov.hmrc.helptosavefrontend.models.TestData.Eligibility._
import uk.gov.hmrc.helptosavefrontend.models.TestData.UserData.{validNSIPayload, validUserInfo}
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.account.AccountNumber
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityCheckResponseAndThreshold
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityCheckResultType.Eligible
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

  val accountNumber = "1234567890123"

  def mockCreateAccount(createAccountRequest: CreateAccountRequest)(response: Either[SubmissionFailure, SubmissionSuccess]): Unit =
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

    "handling getCannotCheckDetailsPage" must {

      "return the cannot check details page" in {
        val result = controller.getCannotCheckDetailsPage(FakeRequest())
        status(result) shouldBe Status.OK
        contentAsString(result) should include("This is because you cannot use the Government Gateway account you signed into")
      }

    }

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
        contentAsString(result) should include("""<a href=/help-to-save/create-account class="link-back">Back</a>""")
      }
    }

    "handling a getCreateAccountPage" must {

      val email = "email"

      val bankDetails = BankDetails(SortCode(1, 2, 3, 4, 5, 6), "12345678", None, "test user name")

      val userInfo = eligibleSpecificReasonCodeWithUserInfo(validUserInfo, 6)

        def doRequest(): Future[PlayResult] =
          controller.getCreateAccountPage()(fakeRequestWithCSRFToken)

      behave like commonEnrolmentAndSessionBehaviour(() ⇒ doRequest())

      checkRedirectIfNoEmailInSession(doRequest())

      "show the user the create account page if the session data contains a confirmed email" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleSpecificReasonCodeWithUserInfo(validUserInfo, 6))), Some(email), None, None, None, Some(bankDetails)))))
          mockSessionCacheConnectorPut(HTSSession(Some(Right(userInfo)), Some(email), None, None, None, Some(bankDetails), accountNumber = None))(Right(()))
        }

        val result = doRequest()
        status(result) shouldBe OK
        contentAsString(result) should include("Accept and create account")
        contentAsString(result) should include("""<a href=/help-to-save/enter-uk-bank-details class="link-back">Back</a>""")
      }

      "show an error page if the eligibility reason cannot be parsed" in {
        val userInfo = eligibleSpecificReasonCodeWithUserInfo(validUserInfo, 999)
        val eligibilityCheckResult = randomEligibility().value.eligibilityCheckResult.copy(reasonCode = 999)
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo)
            .copy(eligible = Eligible(EligibilityCheckResponseAndThreshold(eligibilityCheckResult, randomEligibility().value.threshold))))),
                                                             Some(email), None))))
          mockSessionCacheConnectorPut(HTSSession(Some(Right(userInfo)), Some(email), None, None, None, None, accountNumber = None))(Right(()))
        }

        val result = doRequest()
        checkIsTechnicalErrorPage(result)
      }

      "show the appropriate page content for when user is eligible with reason code 6: UCClaimantAndIncomeSufficient" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleSpecificReasonCodeWithUserInfo(validUserInfo, 6))), Some(email), None, None, None, Some(bankDetails)))))
          mockSessionCacheConnectorPut(HTSSession(Some(Right(userInfo)), Some(email), None, None, None, Some(bankDetails), accountNumber = None))(Right(()))
        }

        val result = doRequest()
        status(result) shouldBe OK
        contentAsString(result) should include("you will tell us each time you leave the UK for 4 weeks or more")
      }

      "show the appropriate page content for when user is eligible with reason code 7: UCClaimantButNoWTC" in {
        val userInfo = eligibleSpecificReasonCodeWithUserInfo(validUserInfo, 7)
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleSpecificReasonCodeWithUserInfo(validUserInfo, 7))), Some(email), None, None, None, Some(bankDetails)))))
          mockSessionCacheConnectorPut(HTSSession(Some(Right(userInfo)), Some(email), None, None, None, Some(bankDetails), accountNumber = None))(Right(()))
        }

        val result = doRequest()
        status(result) shouldBe OK
        contentAsString(result) should include("you will tell us each time you leave the UK for 8 weeks or more")
      }

      "show the appropriate page content for when user is eligible with reason code 8: UCClaimantAndWTC" in {
        val userInfo = eligibleSpecificReasonCodeWithUserInfo(validUserInfo, 8)
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleSpecificReasonCodeWithUserInfo(validUserInfo, 8))), Some(email), None, None, None, Some(bankDetails)))))
          mockSessionCacheConnectorPut(HTSSession(Some(Right(userInfo)), Some(email), None, None, None, Some(bankDetails), accountNumber = None))(Right(()))
        }

        val result = doRequest()
        status(result) shouldBe OK
        contentAsString(result) should include("you will tell us each time you leave the UK for 4 weeks or more")
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
        val eligibilityResult = Some(Right(eligibleSpecificReasonCodeWithUserInfo(validUserInfo, 6)))
        val email = "valid@email.com"
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(eligibilityResult, Some(email), None))))
          mockSessionCacheConnectorPut(HTSSession(Some(Right(userInfo)), Some(email), None, None, None, None, accountNumber = None))(Right(()))
        }
        val result = doRequest()

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.BankAccountController.getBankDetailsPage().url)
      }

    }

    "creating an account" must {
      val confirmedEmail = "confirmed"
      val bankDetails = BankDetails(SortCode(1, 2, 3, 4, 5, 6), "1", None, "name")
      val userInfo = randomEligibleWithUserInfo(validUserInfo)
      val payload = validNSIPayload.updateEmail(confirmedEmail)
        .copy(nbaDetails = Some(bankDetails))
        .copy(version = "V2.0")
        .copy(systemId = "MDTP REGISTRATION")

      val createAccountRequest = CreateAccountRequest(payload, userInfo.eligible.value.eligibilityCheckResult.reasonCode)

        def doCreateAccountRequest(): Future[PlayResult] = controller.createAccount(fakeRequestWithCSRFToken)

      behave like commonEnrolmentAndSessionBehaviour(doCreateAccountRequest)

      checkRedirectIfNoEmailInSession(doCreateAccountRequest())

      "retrieve the user info from session cache and indicate to the user that the creation was successful " +
        "and enrol the user if the creation was successful" in {

          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(userInfo)), Some(confirmedEmail), None, None, None, Some(bankDetails)))))
            mockCreateAccount(createAccountRequest)(Right(SubmissionSuccess(Some(AccountNumber(accountNumber)))))
            mockSessionCacheConnectorPut(HTSSession(Some(Right(userInfo)), Some(confirmedEmail), None, None, None, Some(bankDetails), accountNumber = Some(accountNumber)))(Right(()))
          }

          val result = doCreateAccountRequest()
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.RegisterController.getAccountCreatedPage().url)
        }

      "indicate to the user that account creation was successful " +
        "even if the user couldn't be enrolled into hts at this time" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(userInfo)), Some(confirmedEmail), None, None, None, Some(bankDetails)))))
            mockCreateAccount(createAccountRequest)(Right(SubmissionSuccess(Some(AccountNumber(accountNumber)))))
            mockSessionCacheConnectorPut(HTSSession(Some(Right(userInfo)), Some(confirmedEmail), None, None, None, Some(bankDetails), accountNumber = Some(accountNumber)))(Right(()))
          }

          val result = doCreateAccountRequest()
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.RegisterController.getAccountCreatedPage().url)
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

      "redirect to the create account error page" when {
        "the help to save service returns with an error" in {
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

        "there is an error writing to session" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(userInfo)), Some(confirmedEmail), None, None, None, Some(bankDetails)))))
            mockCreateAccount(createAccountRequest)(Right(SubmissionSuccess(Some(AccountNumber(accountNumber)))))
            mockSessionCacheConnectorPut(HTSSession(Some(Right(userInfo)), Some(confirmedEmail), None, None, None, Some(bankDetails), accountNumber = Some(accountNumber)))(Left(""))
          }

          val result = doCreateAccountRequest()
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.RegisterController.getCreateAccountErrorPage().url)
        }
      }

      "redirect to the create account bank details error page" when {
        "the errorMessageId received in the response from nsi is ZYRC0703" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(userInfo)), Some(confirmedEmail), None, None, None, Some(bankDetails)))))
            mockCreateAccount(createAccountRequest)(Left(SubmissionFailure(Some("ZYRC0703"), "Uh oh", "Uh oh")))
          }

          val result = doCreateAccountRequest()
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.RegisterController.getCreateAccountErrorBankDetailsPage().url)
        }

        "the errorMessageId received in the response from nsi is ZYRC0707" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(userInfo)), Some(confirmedEmail), None, None, None, Some(bankDetails)))))
            mockCreateAccount(createAccountRequest)(Left(SubmissionFailure(Some("ZYRC0707"), "Uh oh", "Uh oh")))
          }

          val result = doCreateAccountRequest()
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.RegisterController.getCreateAccountErrorBankDetailsPage().url)
        }
      }

    }

    "handling getAccountCreatedPage" must {

        def getAccountCreatedPage() = controller.getAccountCreatedPage()(fakeRequestWithCSRFToken)

      "show the page correctly if the person is enrolled to HTS and " +
        "the session has an account number in it" in {
          val accountNumber = UUID.randomUUID().toString

          inSequence{
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None, None, accountNumber = Some(accountNumber)))))
          }

          val result = getAccountCreatedPage()
          status(result) shouldBe OK
          contentAsString(result) should include("Account created")
          contentAsString(result) should include(accountNumber)

        }

      "redirect to check eligibility" when {

        "there is no session data" in {
          inSequence{
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
            mockSessionCacheConnectorGet(Right(None))
          }

          val result = getAccountCreatedPage()
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility().url)
        }

        "there is no account number in session" in {
          inSequence{
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None, None, accountNumber = None))))
          }

          val result = getAccountCreatedPage()
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility().url)
        }

        "the person is not enrolled to HTS" in {
          inSequence{
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          }

          val result = getAccountCreatedPage()
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility().url)
        }

      }

      "show an error page" when {

        "the enrolment status cannot be retrieved" in {
          inSequence{
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Left(""))
          }

          val result = getAccountCreatedPage()
          checkIsTechnicalErrorPage(result)
        }

        "the session data could not be retrieved" in {

          inSequence{
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
            mockSessionCacheConnectorGet(Left(""))
          }

          val result = getAccountCreatedPage()
          checkIsTechnicalErrorPage(result)
        }

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
        contentAsString(result) should include("We cannot create a Help to Save account for you at the moment")
      }
    }

    "handling getCreateAccountErrorBankDetailsPage" must {
      val confirmedEmail = "confirmed"
        def doRequest(): Future[PlayResult] = controller.getCreateAccountErrorBankDetailsPage(FakeRequest())

      behave like commonEnrolmentAndSessionBehaviour(doRequest)

      "show the error page" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), Some(confirmedEmail), None))))
        }

        val result = doRequest()
        contentAsString(result) should include("There is a problem with your bank details")
      }
    }

    "handling checkYourDetails page" must {

      val bankDetails = BankDetails(SortCode(1, 2, 3, 4, 5, 6), "12345678", None, "test user name")

        def doRequest(): Future[PlayResult] = controller.getCreateAccountPage()(fakeRequestWithCSRFToken)

      behave like commonEnrolmentAndSessionBehaviour(() ⇒ doRequest())

      checkRedirectIfNoEmailInSession(doRequest())

      "show the details page for valid users" in {
        val eligibilityResult = Some(Right(randomEligibleWithUserInfo(validUserInfo)))
        val session = HTSSession(eligibilityResult, Some("valid@email.com"), None, None, None, Some(bankDetails))

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(session)))
          mockSessionCacheConnectorPut(HTSSession(eligibilityResult, Some("valid@email.com"), None, None, None, Some(bankDetails), accountNumber = None))(Right(()))
        }
        val result = doRequest()

        status(result) shouldBe 200
        contentAsString(result) should include("Create a Help to Save account")
      }

      "show user not eligible page if the user is not eligible" in {
        val session = HTSSession(Some(Left(randomIneligibility())), None, None, None, None, Some(bankDetails))

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(session)))
        }
        val result = doRequest()

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsNotEligible().url)
      }

      "handle the case when there are no bank details stored in the session" in {
        val eligibilityResult = Some(Right(randomEligibleWithUserInfo(validUserInfo)))
        val session = HTSSession(eligibilityResult, Some("valid@email.com"), None)

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(session)))
          mockSessionCacheConnectorPut(session)(Right(()))
        }
        val result = doRequest()

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.BankAccountController.getBankDetailsPage().url)
      }
    }

    "handling changeEmail" must {
        def doRequest(): Future[PlayResult] = controller.changeEmail()(FakeRequest())

      behave like commonEnrolmentAndSessionBehaviour(() ⇒ doRequest())

      "write a new session and redirect to the select email page" in {
        val eligibilityResult = Some(Right(randomEligibleWithUserInfo(validUserInfo)))
        val session = HTSSession(eligibilityResult, Some("valid@email.com"), None)

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(session)))
          mockSessionCacheConnectorPut(session.copy(changingDetails = true))(Right(()))
        }
        val result = doRequest()

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.getSelectEmailPage().url)
      }

    }

    "handling changeBankDetails" must {
        def doRequest(): Future[PlayResult] = controller.changeBankDetails()(FakeRequest())

      behave like commonEnrolmentAndSessionBehaviour(() ⇒ doRequest())

      "write a new session and redirect to bank details page" in {
        val eligibilityResult = Some(Right(randomEligibleWithUserInfo(validUserInfo)))
        val session = HTSSession(eligibilityResult, Some("valid@email.com"), None)

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(session)))
          mockSessionCacheConnectorPut(session.copy(changingDetails = true))(Right(()))
        }
        val result = doRequest()

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.BankAccountController.getBankDetailsPage().url)
      }

    }
  }
}
