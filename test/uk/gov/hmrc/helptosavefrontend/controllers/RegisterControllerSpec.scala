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
import cats.instances.string._
import cats.syntax.eq._
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.Configuration
import play.api.http.Status
import play.api.mvc.{Result ⇒ PlayResult}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.connectors.NSIProxyConnector.{SubmissionFailure, SubmissionSuccess}
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.{AuthProvider, AuthWithCL200}
import uk.gov.hmrc.helptosavefrontend.models.TestData.Eligibility._
import uk.gov.hmrc.helptosavefrontend.models.TestData.UserData.{validNSIUserInfo, validUserInfo}
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityCheckResult.Eligible
import uk.gov.hmrc.helptosavefrontend.models.userinfo.NSIUserInfo
import uk.gov.hmrc.helptosavefrontend.util.Crypto
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap

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

  def mockCreateAccount(nSIUserInfo: NSIUserInfo)(response: Either[SubmissionFailure, SubmissionSuccess] = Right(SubmissionSuccess(false))): Unit =
    (mockHelpToSaveService.createAccount(_: NSIUserInfo)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nSIUserInfo, *, *)
      .returning(EitherT.fromEither[Future](response))

  def mockEnrolUser()(result: Either[String, Unit]): Unit =
    (mockHelpToSaveService.enrolUser()(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *)
      .returning(EitherT.fromEither[Future](result))

  def mockEmailUpdate(email: String)(result: Either[String, Unit]): Unit =
    (mockHelpToSaveService.storeConfirmedEmail(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(email, *, *)
      .returning(EitherT.fromEither[Future](result))

  def mockAccountCreationAllowed(result: Either[String, UserCapResponse]): Unit =
    (mockHelpToSaveService.isAccountCreationAllowed()(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *)
      .returning(EitherT.fromEither[Future](result))

  def mockUpdateUserCount(result: Either[String, Unit]): Unit =
    (mockHelpToSaveService.updateUserCount()(_: HeaderCarrier, _: ExecutionContext))
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
        mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo.copy(email = None)))), None, None))))
      }

      val result = doRequest
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.RegisterController.getGiveEmailPage().url)
    }
  }

  def checkRedirectIfEmailInSession(doRequest: ⇒ Future[PlayResult]): Unit = {
    "redirect to the confirm email page if the session data contains an email for the user" in {
      inSequence {
        mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
        mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, None))))
      }

      val result = doRequest
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.RegisterController.getSelectEmailPage().url)
    }
  }

  "The RegisterController" when {

    "handling getGiveEmailPage" must {

        def doRequest(): Future[PlayResult] = controller.getGiveEmailPage(fakeRequestWithCSRFToken)

      checkRedirectIfEmailInSession(doRequest())

      "indicate to the user that daily-cap has already reached and account creation not possible" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo.copy(email = None)))), None, None))))
          mockAccountCreationAllowed(Right(UserCapResponse(isDailyCapReached = true)))
        }

        val result = doRequest()
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.RegisterController.getDailyCapReachedPage().url)
      }

      "indicate to the user that total-cap has already reached and account creation not possible" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo.copy(email = None)))), None, None))))
          mockAccountCreationAllowed(Right(UserCapResponse(isTotalCapReached = true)))
        }

        val result = doRequest()
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.RegisterController.getTotalCapReachedPage().url)
      }

      "return the give email page" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo.copy(email = None)))), None, None))))
          mockAccountCreationAllowed(Right(UserCapResponse()))
        }

        val result = doRequest()
        status(result) shouldBe Status.OK
        contentAsString(result) should include("Which email address do you want to use for Help to Save")
        contentAsString(result) should include(routes.RegisterController.giveEmailSubmit().url)
      }

      "indicate to the user that service is unavailable due to both caps are disabled" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo.copy(email = None)))), None, None))))
          mockAccountCreationAllowed(Right(UserCapResponse(isDailyCapDisabled = true, isTotalCapDisabled = true)))
        }

        val result = doRequest()
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.RegisterController.getServiceUnavailablePage().url)
      }

      "skip the cap check at a later point if enable-early-cap-check is set to true" in {
        val controller = newController(earlyCapCheck = true)(crypto)

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo.copy(email = None)))), None, None))))
        }

        val result = controller.getGiveEmailPage(fakeRequestWithCSRFToken)
        status(result) shouldBe Status.OK
      }

    }

    "handling giveEmailSubmit" must {

      val email = "email@test.com"

        def doRequest(email: String): Future[PlayResult] = controller.giveEmailSubmit()(
          fakeRequestWithCSRFToken.withFormUrlEncodedBody("email" → email))

      behave like commonEnrolmentAndSessionBehaviour(() ⇒ doRequest(email))

      checkRedirectIfEmailInSession(doRequest(email))

      "return to the give email page if the form does not contain a valid email" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo.copy(email = None)))), None, None))))
        }

        val result = doRequest("this is not an email")
        status(result) shouldBe Status.OK
        contentAsString(result) should include("Which email address do you want to use for Help to Save")
        contentAsString(result) should include(routes.RegisterController.giveEmailSubmit().url)

      }

      "write the email to session cache redirect to verify email if the form does contains a valid email" in {
        val eligibleWithUserInfo = randomEligibleWithUserInfo(validUserInfo.copy(email = None))
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithUserInfo)), None, None))))
          mockSessionCacheConnectorPut(HTSSession(Some(Right(eligibleWithUserInfo)), None, Some(email)))(Right(()))
        }

        val result = doRequest(email)
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.NewApplicantUpdateEmailAddressController.verifyEmail().url)
      }

      "show an error page if the form does contains a valid email but the write to session cache fails" in {
        val eligibleWithUserInfo = randomEligibleWithUserInfo(validUserInfo.copy(email = None))
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithUserInfo)), None, None))))
          mockSessionCacheConnectorPut(HTSSession(Some(Right(eligibleWithUserInfo)), None, Some(email)))(Left(""))
        }

        val result = doRequest(email)
        checkIsTechnicalErrorPage(result)
      }

    }

    "handling getSelectEmailPage" must {

        def doRequest(): Future[PlayResult] = controller.getSelectEmailPage(fakeRequestWithCSRFToken)

      behave like commonEnrolmentAndSessionBehaviour(() ⇒ doRequest())

      checkRedirectIfNoEmailInSession(doRequest())

      "indicate to the user that user-cap has already reached and account creation not possible" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, None))))
          mockAccountCreationAllowed(Right(UserCapResponse(isTotalCapReached = true)))
        }

        val result = doRequest()
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.RegisterController.getTotalCapReachedPage().url)
      }

      "return the confirm email page" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, None))))
          mockAccountCreationAllowed(Right(UserCapResponse()))
        }

        val result = doRequest()
        status(result) shouldBe Status.OK
        contentAsString(result) should include("Which email address do you want us to use for your Help to Save account?")
      }

      "handle the case when the email is invalid" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo.copy(email = Some("invalid@email"))))), None, None))))
        }

        val result = doRequest()
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.RegisterController.getGiveEmailPage().url)
      }

      "skip the cap check at a later point if enable-early-cap-check is set to true" in {
        val controller = newController(earlyCapCheck = true)(crypto)

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo.copy(email = Some("test@user.com"))))), None, None))))
        }

        val result = controller.getSelectEmailPage(fakeRequestWithCSRFToken)
        status(result) shouldBe Status.OK
      }

    }

    "handling getSelectEmailSubmit" must {

        def doRequest(newEmail: Option[String]): Future[PlayResult] = {
          newEmail.fold(
            controller.selectEmailSubmit()(fakeRequestWithCSRFToken.withFormUrlEncodedBody("email" → "Yes"))
          ) { e ⇒
              controller.selectEmailSubmit()(fakeRequestWithCSRFToken.withFormUrlEncodedBody("email" → "No", "new-email" → e))
            }

        }

      behave like commonEnrolmentAndSessionBehaviour(() ⇒ doRequest(None))

      checkRedirectIfNoEmailInSession(doRequest(None))

      "redirect to confirm email if the session data shows that they have been already found to be eligible " +
        "and the form contains no new email" in {
          val encryptedEmail = "encrypted"
          val crypto = mock[Crypto]
          val controller = newController(earlyCapCheck = true)(crypto)

          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, None))))
            (crypto.encrypt(_: String)).expects(emailStr).returning(encryptedEmail)
          }

          val result = controller.selectEmailSubmit()(fakeRequestWithCSRFToken.withFormUrlEncodedBody("email" → "Yes"))
          status(result) shouldBe Status.SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.RegisterController.confirmEmail(encryptedEmail).url)
        }

      "redirect to verify email if the session data shows that they have been already found to be eligible " +
        "and the form contains a valid new email" in {
          val newEmail = "email@test.com"
          val eligibleWithUserInfo = randomEligibleWithUserInfo(validUserInfo)
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithUserInfo)), None, None))))
            mockSessionCacheConnectorPut(HTSSession(Some(Right(eligibleWithUserInfo)), None, Some(newEmail)))(Right(()))

          }

          val result = doRequest(Some(newEmail))
          status(result) shouldBe Status.SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.NewApplicantUpdateEmailAddressController.verifyEmail().url)
        }

      "show an error page if writing the pending email to session cache fails" in {
        val newEmail = "email@test.com"
        val eligibleWithUserInfo = randomEligibleWithUserInfo(validUserInfo)
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithUserInfo)), None, None))))
          mockSessionCacheConnectorPut(HTSSession(Some(Right(eligibleWithUserInfo)), None, Some(newEmail)))(Left(""))

        }

        val result = doRequest(Some(newEmail))
        checkIsTechnicalErrorPage(result)
      }

      "redirect to the confirm email page if the session data shows that they have been already found to be eligible and " when {

        "the form contains a invalid new email" in {
          val invalidEmail = "not-an-email"

          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, None))))
          }

          val result = doRequest(Some(invalidEmail))
          status(result) shouldBe Status.OK
          contentAsString(result) should include("Which email")
        }

        "no option has been selected" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, None))))
          }

          val result = controller.selectEmailSubmit()(fakeRequestWithCSRFToken.withFormUrlEncodedBody("new-email" → "email@test.com"))

          status(result) shouldBe Status.OK
          contentAsString(result) should include("Which email")
        }

        "a new email has been selected but there is no new email given" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, None))))
          }

          val result = controller.selectEmailSubmit()(fakeRequestWithCSRFToken.withFormUrlEncodedBody("email" → "No"))

          status(result) shouldBe Status.OK
          contentAsString(result) should include("Which email")
        }

        "the 'email' key of the form is not 'Yes' or 'No'" in {
          forAll { s: String ⇒
            whenever(s =!= "Yes" | s =!= "No") {
              inSequence {
                mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
                mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
                mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, None))))
              }

              val result = controller.selectEmailSubmit()(fakeRequestWithCSRFToken.withFormUrlEncodedBody("email" → s))

              status(result) shouldBe Status.OK
              contentAsString(result) should include("Which email")

            }

          }
        }

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
        contentAsString(result) should include("Service Unavailable")
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

    "handling a confirmEmail" must {

      val email = "email"

        def doRequest(email: String): Future[PlayResult] =
          controller.confirmEmail(email)(FakeRequest())

      behave like commonEnrolmentAndSessionBehaviour(() ⇒ doRequest(email))

      checkRedirectIfNoEmailInSession(doRequest(email))

      "write the email to keystore and the email store if the user has not already enrolled and " +
        "the session data shows that they have been already found to be eligible" in {
          val eligibleWithUserInfo = randomEligibleWithUserInfo(validUserInfo)
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithUserInfo)), None, None))))
            mockSessionCacheConnectorPut(HTSSession(Some(Right(eligibleWithUserInfo)), Some(email), None))(Right(CacheMap("", Map.empty)))
            mockEmailUpdate(email)(Left(""))
          }
          await(doRequest(crypto.encrypt(email)))
        }

      "redirect to the create an account page if the write to keystore and the email store was successful" in {
        val eligibleWithUserInfo = randomEligibleWithUserInfo(validUserInfo)

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithUserInfo)), None, None))))
          mockSessionCacheConnectorPut(HTSSession(Some(Right(eligibleWithUserInfo)), Some(email), None))(Right(CacheMap("", Map.empty)))
          mockEmailUpdate(email)(Right(()))
        }

        val result = doRequest(crypto.encrypt(email))
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.RegisterController.getCreateAccountHelpToSavePage().url)
      }

      "return an error" when {

        "the email cannot be decrypted" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, None))))
          }

          val result = doRequest("notencrypted")
          checkIsTechnicalErrorPage(result)
        }

        "the email cannot be written to keystore" in {
          val eligibleWithUserInfo = randomEligibleWithUserInfo(validUserInfo)
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithUserInfo)), None, None))))
            mockSessionCacheConnectorPut(HTSSession(Some(Right(eligibleWithUserInfo)), Some(email), None))(Left(""))
          }

          val result = doRequest(crypto.encrypt(email))
          checkIsTechnicalErrorPage(result)
        }

        "the email cannot be written to the email store" in {
          val eligibleWithUserInfo = randomEligibleWithUserInfo(validUserInfo)
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithUserInfo)), None, None))))
            mockSessionCacheConnectorPut(HTSSession(Some(Right(eligibleWithUserInfo)), Some(email), None))(Right(CacheMap("", Map.empty)))
            mockEmailUpdate(email)(Left(""))
          }

          val result = doRequest(crypto.encrypt(email))
          checkIsTechnicalErrorPage(result)
        }
      }
    }

    "handling a getCreateAccountHelpToSave" must {

      val email = "email"

        def doRequest(): Future[PlayResult] =
          controller.getCreateAccountHelpToSavePage()(fakeRequestWithCSRFToken)

      behave like commonEnrolmentAndSessionBehaviour(() ⇒ doRequest())

      checkRedirectIfNoEmailInSession(doRequest())

      "redirect the user to the confirm details page if there is no email in the session data" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, None))))
        }

        val result = doRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.RegisterController.getSelectEmailPage().url)
      }

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

        def doCreateAccountRequest(): Future[PlayResult] = controller.createAccountHelpToSave(FakeRequest())

      behave like commonEnrolmentAndSessionBehaviour(doCreateAccountRequest)

      checkRedirectIfNoEmailInSession(doCreateAccountRequest())

      "retrieve the user info from session cache and indicate to the user that the creation was successful " +
        "and enrol the user if the creation was successful" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), Some(confirmedEmail), None))))
            mockCreateAccount(validNSIUserInfo.updateEmail(confirmedEmail))()
            mockUpdateUserCount(Right(Unit))
            mockEnrolUser()(Right(()))
          }

          val result = doCreateAccountRequest()
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(appConfig.nsiManageAccountUrl)
        }

      "indicate to the user that account creation was successful " +
        "even if the user couldn't be enrolled into hts at this time" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), Some(confirmedEmail), None))))
            mockCreateAccount(validNSIUserInfo.updateEmail(confirmedEmail))()
            mockUpdateUserCount(Right(Unit))
            mockEnrolUser()(Left("Oh no"))
          }

          val result = doCreateAccountRequest()
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(appConfig.nsiManageAccountUrl)
        }

      "not update user counts but enrol the user if the user already had an account" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), Some(confirmedEmail), None))))
          mockCreateAccount(validNSIUserInfo.updateEmail(confirmedEmail))(Right(SubmissionSuccess(alreadyHadAccount = true)))
          mockEnrolUser()(Right(()))
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
        redirectLocation(result) shouldBe Some(routes.RegisterController.getSelectEmailPage().url)
      }

      "redirect to the create account error page" when {

        "the help to save service returns with an error" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), Some(confirmedEmail), None))))
            mockCreateAccount(validNSIUserInfo.updateEmail(confirmedEmail))(Left(SubmissionFailure(None, "Uh oh", "Uh oh")))
          }

          val result = doCreateAccountRequest()
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.RegisterController.getCreateAccountErrorPage().url)
        }
      }

      "handling getCreateAccountErrorPage" must {

          def doRequest(): Future[PlayResult] = controller.getCreateAccountErrorPage(FakeRequest())

        behave like commonEnrolmentAndSessionBehaviour(doRequest)

        "show the error page" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), Some(confirmedEmail), None))))
          }

          val result = doRequest()
          contentAsString(result) should include("We couldn&#x27;t create a Help to Save account for you at this time")

        }

      }

    }
  }
}
